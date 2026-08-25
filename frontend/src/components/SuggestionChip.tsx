import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { AIClassification } from '../api/types'

interface Props {
  issueId: number
  /** Called whenever the issue's own values changed, so the board can drop its stale copy. */
  onIssueChanged: (issueVersion: number) => void
}

/** How often to ask, and for how long, while the classifier has not answered. */
const POLL_MS = 2000
const GIVE_UP_MS = 30000

type Phase = 'waiting' | 'gaveUp' | 'ready' | 'failed'

const REVIEW_LABEL: Record<AIClassification['reviewStatus'], string> = {
  PENDING: 'Suggested',
  AUTO_APPLIED: 'Applied automatically',
  CONFIRMED: 'Accepted',
  OVERRIDDEN: 'You kept your own',
}

/**
 * What the AI thought, and the two buttons that answer it.
 *
 * The classification arrives over a queue seconds after the issue is created, so there is nothing
 * to render at first. This polls rather than holding a socket open: a push channel is the
 * notification service, which PROJECT.md puts in Stretch, and a suggestion that lands two seconds
 * late is not worth a WebSocket.
 *
 * It gives up after 30 seconds instead of polling forever. A suggestion that never comes means the
 * message is parked in the dead-letter queue, and saying "no suggestion yet" is honest where a
 * spinner that never stops is not.
 */
export function SuggestionChip({ issueId, onIssueChanged }: Props) {
  const [classification, setClassification] = useState<AIClassification | null>(null)
  const [phase, setPhase] = useState<Phase>('waiting')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const startedAt = useRef(Date.now())

  const poll = useCallback(async () => {
    try {
      const found = await api.classification(issueId)
      if (found !== null) {
        setClassification(found)
        setPhase('ready')
        return true
      }
      return false
    } catch (err) {
      if (err instanceof ApiError) setError(err.message)
      setPhase('failed')
      return true
    }
  }, [issueId])

  useEffect(() => {
    let cancelled = false
    startedAt.current = Date.now()
    setClassification(null)
    setPhase('waiting')
    setError(null)

    // A plain interval would keep firing while a slow request is still in flight. Chaining the
    // next timer only after the previous answer lands keeps at most one request outstanding.
    let timer: number

    async function tick() {
      if (cancelled) return
      const done = await poll()
      if (cancelled || done) return

      if (Date.now() - startedAt.current > GIVE_UP_MS) {
        setPhase('gaveUp')
        return
      }
      timer = window.setTimeout(tick, POLL_MS)
    }

    void tick()
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [poll])

  async function decide(accept: boolean) {
    setBusy(true)
    setError(null)
    try {
      const updated = accept
        ? await api.acceptClassification(issueId)
        : await api.overrideClassification(issueId)
      setClassification(updated)
      // Accepting writes the suggested values onto the issue, which bumps its version. The board
      // has to take the new one or the next drag on this card sends a stale version and gets a
      // false 409 — the same bug this project has already hit twice.
      if (accept) onIssueChanged(updated.issueVersion)
    } catch (err) {
      if (err instanceof ApiError) setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  function retry() {
    startedAt.current = Date.now()
    setPhase('waiting')
    void poll()
  }

  if (phase === 'waiting') {
    return (
      <div className="chip chip--waiting">
        <span className="chip__eyebrow">Triage</span>
        <p className="chip__line">Reading the ticket…</p>
      </div>
    )
  }

  if (phase === 'gaveUp' || phase === 'failed') {
    return (
      <div className="chip">
        <span className="chip__eyebrow">Triage</span>
        <p className="chip__line">{error ?? 'No suggestion came back.'}</p>
        <button type="button" className="link" onClick={retry}>
          Check again
        </button>
      </div>
    )
  }

  if (classification === null) return null

  const decided =
    classification.reviewStatus === 'CONFIRMED' || classification.reviewStatus === 'OVERRIDDEN'
  const confidence = Math.round(classification.confidence * 100)
  const missing = classification.missingInfo ?? []

  return (
    <div className="chip chip--ready">
      <div className="chip__head">
        <span className="chip__eyebrow">{REVIEW_LABEL[classification.reviewStatus]}</span>
        {/* Which model said this. "stub-v1" means keyword rules, not AI — printed so the screen
            itself says so and the stub can never be mistaken for the real thing. */}
        <span className="chip__model" title="The model that produced this">
          {classification.modelVersion} · {confidence}%
        </span>
      </div>

      <dl className="chip__values">
        {classification.suggestedType && (
          <div className="chip__value">
            <dt>Type</dt>
            <dd>{classification.suggestedType.toLowerCase()}</dd>
          </div>
        )}
        {classification.suggestedPriority && (
          <div className="chip__value">
            <dt>Priority</dt>
            <dd>{classification.suggestedPriority.toLowerCase()}</dd>
          </div>
        )}
        {classification.suggestedTeam && (
          <div className="chip__value">
            <dt>Team</dt>
            <dd>{classification.suggestedTeam}</dd>
          </div>
        )}
        {classification.effortHint && (
          <div className="chip__value">
            <dt>Effort</dt>
            <dd>{classification.effortHint.toLowerCase()}</dd>
          </div>
        )}
      </dl>

      {missing.length > 0 && (
        <div className="chip__missing">
          <span className="chip__missing-label">The ticket does not say</span>
          <ul>
            {missing.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </div>
      )}

      {error && <p className="chip__error">{error}</p>}

      {!decided && (
        <div className="chip__actions">
          <button type="button" className="button" disabled={busy} onClick={() => void decide(true)}>
            {classification.reviewStatus === 'AUTO_APPLIED' ? 'Looks right' : 'Accept'}
          </button>
          <button type="button" className="link" disabled={busy} onClick={() => void decide(false)}>
            Keep mine
          </button>
        </div>
      )}
    </div>
  )
}
