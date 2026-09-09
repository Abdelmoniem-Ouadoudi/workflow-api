import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import type { IssueComment, IssueStatusChange, IssueSummary, Status, User } from '../api/types'
import { STATUS_LABEL } from '../api/types'
import type { Session } from '../auth/session'
import { SuggestionChip } from './SuggestionChip'

const timeFormatter = new Intl.DateTimeFormat(undefined, {
  month: 'short',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

interface Props {
  issue: IssueSummary
  users: User[]
  session: Session
  onClose: () => void
  onAssigneeChanged: (assigneeId: number | null, version: number) => void
  onAssignFailed: (message: string) => void
  /** The AI changed the issue itself, so the board must drop its cached copy. */
  onIssueChanged: () => void
}

/**
 * The manifest sheet for one issue. Status is not editable here — the board's drag is the
 * only path that changes it, so this panel does not offer a second one that could disagree
 * with the workflow map.
 */
export function IssueDetail({
  issue,
  users,
  session,
  onClose,
  onAssigneeChanged,
  onAssignFailed,
  onIssueChanged,
}: Props) {
  const [comments, setComments] = useState<IssueComment[] | null>(null)
  const [history, setHistory] = useState<IssueStatusChange[] | null>(null)
  const [draft, setDraft] = useState('')
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editDraft, setEditDraft] = useState('')
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const confirmTimer = useRef<number | undefined>(undefined)

  // Both lists are the same request pattern against the same issue, so they load together and
  // the drawer is drawn once. The status the header shows is the end of the history below it.
  useEffect(() => {
    let cancelled = false
    setComments(null)
    setHistory(null)
    api
      .listComments(issue.id)
      .then((list) => {
        if (!cancelled) setComments(list)
      })
      .catch((err) => {
        if (!cancelled && err instanceof ApiError) setError(err.message)
      })
    api
      .listHistory(issue.id)
      .then((list) => {
        if (!cancelled) setHistory(list)
      })
      .catch((err) => {
        if (!cancelled && err instanceof ApiError) setError(err.message)
      })
    return () => {
      cancelled = true
    }
  }, [issue.id])

  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  useEffect(() => () => window.clearTimeout(confirmTimer.current), [])

  function authorName(id: number): string {
    return users.find((user) => user.id === id)?.username ?? `user ${id}`
  }

  async function handleAssign(value: string) {
    const userId = value === '' ? null : Number(value)
    try {
      // The save also bumps the row's version, so the board's cached copy must move too —
      // otherwise the next drag on this card sends a stale version and gets a false 409.
      const updated = await api.assignIssue(issue.id, userId)
      onAssigneeChanged(updated.assigneeId, updated.version)
    } catch (err) {
      if (err instanceof ApiError) onAssignFailed(err.message)
    }
  }

  async function handlePost(event: FormEvent) {
    event.preventDefault()
    if (draft.trim() === '') return
    try {
      // No author is sent: the server reads it from the token. Whoever is signed in is the author.
      const created = await api.createComment(issue.id, draft)
      setComments((current) => [...(current ?? []), created])
      setDraft('')
    } catch (err) {
      if (err instanceof ApiError) setError(err.message)
    }
  }

  function startEdit(comment: IssueComment) {
    setEditingId(comment.id)
    setEditDraft(comment.content)
  }

  async function saveEdit(comment: IssueComment) {
    if (editDraft.trim() === '') return
    try {
      const updated = await api.updateComment(issue.id, comment.id, editDraft)
      setComments((current) =>
        (current ?? []).map((existing) => (existing.id === comment.id ? updated : existing)),
      )
      setEditingId(null)
    } catch (err) {
      if (err instanceof ApiError) setError(err.message)
    }
  }

  function requestDelete(id: number) {
    if (confirmDeleteId === id) {
      window.clearTimeout(confirmTimer.current)
      void (async () => {
        try {
          await api.deleteComment(issue.id, id)
          setComments((current) => (current ?? []).filter((comment) => comment.id !== id))
        } catch (err) {
          if (err instanceof ApiError) setError(err.message)
        } finally {
          setConfirmDeleteId(null)
        }
      })()
      return
    }
    setConfirmDeleteId(id)
    window.clearTimeout(confirmTimer.current)
    confirmTimer.current = window.setTimeout(() => setConfirmDeleteId(null), 3000)
  }

  return (
    <div className="drawer">
      <div className="drawer__backdrop" onClick={onClose} />
      <aside className="drawer__panel" role="dialog" aria-label={`${issue.issueKey} detail`}>
        <div className="drawer__head">
          <span className="drawer__key">{issue.issueKey}</span>
          <button type="button" className="drawer__close" onClick={onClose} aria-label="Close">
            ×
          </button>
        </div>

        <h2 className="drawer__title">{issue.title}</h2>

        <div className="drawer__badges">
          <span className="drawer__badge">{issue.type.toLowerCase()}</span>
          <span className="drawer__badge">{STATUS_LABEL[issue.status as Status]}</span>
          <span className={`drawer__badge${issue.priority === 'CRITICAL' ? ' drawer__badge--stop' : ''}`}>
            {issue.priority.toLowerCase()}
          </span>
        </div>

        {error && <p className="drawer__error">{error}</p>}

        {/* What the AI made of this ticket. It arrives over a queue seconds after the issue is
            created, so this polls rather than expecting it to be there already. */}
        <SuggestionChip issueId={issue.id} onIssueChanged={onIssueChanged} />

        <div className="drawer__field">
          <label className="drawer__label" htmlFor="assignee">
            Assigned to
          </label>
          <select
            id="assignee"
            className="compose__select"
            value={issue.assigneeId ?? ''}
            onChange={(event) => void handleAssign(event.target.value)}
          >
            <option value="">Unassigned</option>
            {users.map((user) => (
              <option key={user.id} value={user.id}>
                {user.username}
              </option>
            ))}
          </select>
        </div>

        <div className="drawer__section">
          <h3 className="drawer__label">Discussion</h3>

          {comments === null && <p className="drawer__loading">Loading…</p>}

          {comments !== null && comments.length === 0 && (
            <p className="drawer__loading">No comments yet. Add the first one.</p>
          )}

          <ul className="comments">
            {comments?.map((comment) => (
              <li className="comment" key={comment.id}>
                <div className="comment__head">
                  <span className="comment__author">{authorName(comment.authorId)}</span>
                  <span className="comment__time">{timeFormatter.format(new Date(comment.createdAt))}</span>
                </div>

                {editingId === comment.id ? (
                  <div className="comment__edit">
                    <textarea
                      className="drawer__textarea"
                      value={editDraft}
                      onChange={(event) => setEditDraft(event.target.value)}
                      rows={3}
                    />
                    <div className="comment__edit-actions">
                      <button type="button" className="link" onClick={() => setEditingId(null)}>
                        Cancel
                      </button>
                      <button type="button" className="link" onClick={() => void saveEdit(comment)}>
                        Save
                      </button>
                    </div>
                  </div>
                ) : (
                  <>
                    <p className="comment__body">{comment.content}</p>
                    <div className="comment__actions">
                      <button type="button" className="link" onClick={() => startEdit(comment)}>
                        Edit
                      </button>
                      <button
                        type="button"
                        className={`link${confirmDeleteId === comment.id ? ' link--stop' : ''}`}
                        onClick={() => requestDelete(comment.id)}
                      >
                        {confirmDeleteId === comment.id ? 'Remove — sure?' : 'Remove'}
                      </button>
                    </div>
                  </>
                )}
              </li>
            ))}
          </ul>

          <form className="comment-compose" onSubmit={handlePost}>
            <textarea
              className="drawer__textarea"
              placeholder="Add a comment…"
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              rows={2}
              aria-label="New comment"
            />
            <div className="comment-compose__row">
              {/* Was a "commenting as" dropdown. The server takes the author from the token now,
                  so the choice was never real — it only let you sign someone else's name. */}
              <span className="comment-compose__as">
                as <strong>{session.username}</strong>
              </span>
              <button type="submit" className="button" disabled={draft.trim() === ''}>
                Comment
              </button>
            </div>
          </form>
        </div>

        {/* Under the discussion, because it answers the question the discussion raises: not what
            people said about the card, but what actually happened to it. Read-only — there is
            nothing to edit, and the API offers no way to. */}
        <div className="drawer__section">
          <h3 className="drawer__label">History</h3>

          {history === null && <p className="drawer__loading">Loading…</p>}

          {history !== null && history.length === 0 && (
            <p className="drawer__loading">Nobody has moved this card yet.</p>
          )}

          <ol className="history">
            {history?.map((move) => (
              <li className="history__row" key={move.id}>
                <span className="history__move">
                  <span className="history__status">{STATUS_LABEL[move.fromStatus]}</span>
                  <span className="history__arrow" aria-hidden="true">
                    →
                  </span>
                  <span className="history__status history__status--to">
                    {STATUS_LABEL[move.toStatus]}
                  </span>
                </span>
                <span className="history__who">
                  {/* The name is on the row, not looked up in `users`: whoever moved the card
                      may have left the project since, and "user 7" is not an answer. */}
                  by <strong>{move.changedByUsername}</strong>
                </span>
                <span className="history__time">
                  {timeFormatter.format(new Date(move.changedAt))}
                </span>
              </li>
            ))}
          </ol>
        </div>
      </aside>
    </div>
  )
}
