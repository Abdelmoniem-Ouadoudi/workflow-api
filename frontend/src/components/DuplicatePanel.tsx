import { useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { SimilarIssue } from '../api/types'

interface Props {
  /** What the person has typed so far — title, and description if there is one. */
  text: string
  projectKey: string
  /** Opens the existing ticket instead of filing a new one. The point of the whole feature. */
  onOpen: (issueId: number) => void
}

/** Long enough to mean something. Below this every ticket looks like every other ticket. */
const MIN_LENGTH = 10

/** Wait for a pause in typing rather than searching on every keystroke. */
const DEBOUNCE_MS = 500

/**
 * "Somebody may have already reported this."
 *
 * Asks while the ticket is still being written, because the answer is worthless once it has been
 * filed. Three rules keep it from being the kind of panel people learn to scroll past:
 *
 * - it says nothing at all when there is no match, which is the common case
 * - it waits for a pause in typing, so it is not searching on every keystroke
 * - it never reports its own errors. A duplicate check that could not run is not the writer's
 *   problem, and an error box over a form somebody is filling in is worse than silence
 */
export function DuplicatePanel({ text, projectKey, onOpen }: Props) {
  const [matches, setMatches] = useState<SimilarIssue[]>([])

  useEffect(() => {
    const trimmed = text.trim()
    if (trimmed.length < MIN_LENGTH) {
      setMatches([])
      return
    }

    // One controller per attempt. When the text changes the previous request is aborted, so a slow
    // answer for older text can never arrive after a fast answer for newer text and overwrite it.
    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      api
        .similarIssues(trimmed, projectKey, controller.signal)
        .then(setMatches)
        .catch((error) => {
          // Aborts are this component's own doing. Anything else is a real failure, and the right
          // response is still to show nothing: the person is trying to write a ticket.
          if (error instanceof DOMException && error.name === 'AbortError') return
          if (error instanceof ApiError) setMatches([])
        })
    }, DEBOUNCE_MS)

    return () => {
      window.clearTimeout(timer)
      controller.abort()
    }
  }, [text, projectKey])

  // Silence is the correct output most of the time.
  if (matches.length === 0) return null

  return (
    <aside className="dupes" aria-label="Possible duplicates">
      <p className="dupes__lead">
        {matches.length === 1
          ? 'This looks like a ticket that already exists.'
          : `This looks like ${matches.length} tickets that already exist.`}
      </p>

      <ul className="dupes__list">
        {matches.map((match) => (
          <li key={match.issueId}>
            <button type="button" className="dupes__row" onClick={() => onOpen(match.issueId)}>
              <span className="dupes__key">{match.issueKey}</span>
              <span className="dupes__title">{match.title}</span>
              {/* The score is shown, not hidden. A borderline match is worth judging for
                  yourself, where a bare list asks you to take it on trust. */}
              <span className="dupes__score">{Math.round(match.score * 100)}% alike</span>
            </button>
          </li>
        ))}
      </ul>

      <p className="dupes__hint">Open one to add to it instead, or carry on writing.</p>
    </aside>
  )
}
