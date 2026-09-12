import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Navigate, useParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import type {
  IssueComment,
  IssueDetail,
  IssueStatusChange,
  Project,
  ProjectMember,
  Status,
} from '../api/types'
import { STATUS_LABEL } from '../api/types'
import { useCurrentSession } from '../auth/SessionContext'
import { Notice } from './Notice'
import type { NoticeState } from './Notice'
import { SuggestionChip } from './SuggestionChip'
import { Avatar, Page, PriorityPill } from './ui'

const timeFormatter = new Intl.DateTimeFormat(undefined, {
  month: 'short',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

const dayFormatter = new Intl.DateTimeFormat(undefined, {
  month: 'short',
  day: 'numeric',
  year: 'numeric',
})

const STATUS_PILL: Record<Status, string> = {
  TO_DO: 'pill pill--case',
  IN_PROGRESS: 'pill pill--case pill--blue',
  DONE: 'pill pill--case pill--green',
}

function capitalise(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase()
}

/**
 * One issue, on a page of its own.
 *
 * Status is shown but not editable here. The board's drag is the only path that changes it, so
 * this page does not offer a second one that could disagree with the workflow map.
 */
export function IssuePage() {
  const params = useParams<{ id: string; issueId: string }>()
  const projectId = Number(params.id)
  const issueId = Number(params.issueId)
  const session = useCurrentSession()

  const [issue, setIssue] = useState<IssueDetail | null>(null)
  const [project, setProject] = useState<Project | null>(null)
  const [members, setMembers] = useState<ProjectMember[]>([])
  const [comments, setComments] = useState<IssueComment[] | null>(null)
  const [history, setHistory] = useState<IssueStatusChange[] | null>(null)
  const [notice, setNotice] = useState<NoticeState | null>(null)
  const [loading, setLoading] = useState(true)

  const [draft, setDraft] = useState('')
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editDraft, setEditDraft] = useState('')
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null)
  const confirmTimer = useRef<number | undefined>(undefined)

  function fail(error: unknown) {
    if (error instanceof ApiError) setNotice({ tone: 'stop', message: error.message })
  }

  /** Re-read only the issue: after an accepted suggestion its type and priority have changed. */
  const reloadIssue = useCallback(async () => {
    try {
      setIssue(await api.getIssue(issueId))
    } catch (error) {
      fail(error)
    }
  }, [issueId])

  useEffect(() => {
    let cancelled = false
    async function start() {
      setLoading(true)
      setComments(null)
      setHistory(null)
      try {
        // The issue first, alone: it says which project it is really in. A hand-edited URL with
        // the wrong project id is then redirected below, instead of failing on that project's
        // member list before the redirect could happen.
        const loaded = await api.getIssue(issueId)
        if (cancelled) return
        setIssue(loaded)
        if (loaded.projectId !== projectId) return

        // Then the rest in one round. Members are needed for names: a comment carries only its
        // author's id, and the assignee dropdown lists who can take the work.
        const [opened, people, discussion, moves] = await Promise.all([
          api.getProject(projectId),
          api.projectMembers(projectId),
          api.listComments(issueId),
          api.listHistory(issueId),
        ])
        if (cancelled) return
        setProject(opened)
        setMembers(people)
        setComments(discussion)
        setHistory(moves)
      } catch (error) {
        if (!cancelled) fail(error)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    void start()
    return () => {
      cancelled = true
    }
  }, [issueId, projectId])

  useEffect(() => () => window.clearTimeout(confirmTimer.current), [])

  function nameOf(userId: number): string {
    return members.find((member) => member.userId === userId)?.username ?? `user ${userId}`
  }

  async function handleAssign(value: string) {
    if (issue === null) return
    const userId = value === '' ? null : Number(value)
    try {
      const updated = await api.assignIssue(issue.id, userId)
      setIssue({ ...issue, assigneeId: updated.assigneeId, version: updated.version })
    } catch (error) {
      fail(error)
    }
  }

  async function handlePost(event: FormEvent) {
    event.preventDefault()
    if (draft.trim() === '') return
    try {
      // No author is sent: the server reads it from the token. Whoever is signed in is the author.
      const created = await api.createComment(issueId, draft)
      setComments((current) => [...(current ?? []), created])
      setDraft('')
    } catch (error) {
      fail(error)
    }
  }

  async function saveEdit(comment: IssueComment) {
    if (editDraft.trim() === '') return
    try {
      const updated = await api.updateComment(issueId, comment.id, editDraft)
      setComments((current) =>
        (current ?? []).map((existing) => (existing.id === comment.id ? updated : existing)),
      )
      setEditingId(null)
    } catch (error) {
      fail(error)
    }
  }

  function requestDelete(id: number) {
    if (confirmDeleteId === id) {
      window.clearTimeout(confirmTimer.current)
      void (async () => {
        try {
          await api.deleteComment(issueId, id)
          setComments((current) => (current ?? []).filter((comment) => comment.id !== id))
        } catch (error) {
          fail(error)
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

  const boardCrumb = { label: project?.name ?? 'Board', to: `/projects/${projectId}/board` }

  if (loading) {
    return (
      <Page crumbs={[boardCrumb, { label: 'Issue' }]}>
        <p className="loading">Loading the issue…</p>
      </Page>
    )
  }

  if (issue === null) {
    return (
      <Page crumbs={[boardCrumb, { label: 'Issue' }]}>
        <Notice notice={notice} onDismiss={() => setNotice(null)} />
      </Page>
    )
  }

  // /projects/1/issues/42 where issue 42 is in project 3: go to the address that is true, so the
  // breadcrumb, the sidebar and the member list all describe the right project.
  if (issue.projectId !== projectId) {
    return <Navigate to={`/projects/${issue.projectId}/issues/${issue.id}`} replace />
  }

  const activeMembers = members.filter((member) => member.active)

  return (
    <Page crumbs={[boardCrumb, { label: issue.issueKey }]}>
      <div className="issue-layout">
        <div className="issue-main">
          <div className="issue-head__meta">
            <span>{issue.issueKey}</span>
            <span className="pill">{issue.type}</span>
            <PriorityPill priority={issue.priority} />
          </div>
          <h1 className="issue-head__title">{issue.title}</h1>

          <Notice notice={notice} onDismiss={() => setNotice(null)} />

          <section className="card">
            <h2 className="card__title">Description</h2>
            <p className="prose">{issue.description?.trim() || 'No description.'}</p>
          </section>

          <section className="card">
            <h2 className="card__title">
              Discussion{comments && comments.length > 0 ? ` (${comments.length})` : ''}
            </h2>

            {comments !== null && comments.length === 0 && (
              <p className="empty">No comments yet. Add the first one.</p>
            )}

            <ul className="comments">
              {comments?.map((comment) => (
                <li className="comment" key={comment.id}>
                  <Avatar name={nameOf(comment.authorId)} size="md" />
                  <div className="comment__main">
                    <div className="comment__head">
                      <span className="comment__author">{nameOf(comment.authorId)}</span>
                      <span className="comment__time">
                        {timeFormatter.format(new Date(comment.createdAt))}
                      </span>
                    </div>

                    {editingId === comment.id ? (
                      <div className="comment__edit">
                        <textarea
                          className="textarea"
                          value={editDraft}
                          onChange={(event) => setEditDraft(event.target.value)}
                          rows={3}
                        />
                        <div className="comment__actions">
                          <button type="button" className="link" onClick={() => void saveEdit(comment)}>
                            Save
                          </button>
                          <button
                            type="button"
                            className="link link--quiet"
                            onClick={() => setEditingId(null)}
                          >
                            Cancel
                          </button>
                        </div>
                      </div>
                    ) : (
                      <>
                        <p className="comment__body">{comment.content}</p>
                        <div className="comment__actions">
                          <button
                            type="button"
                            className="link"
                            onClick={() => {
                              setEditingId(comment.id)
                              setEditDraft(comment.content)
                            }}
                          >
                            Edit
                          </button>
                          <button
                            type="button"
                            className={`link ${confirmDeleteId === comment.id ? 'link--stop' : 'link--quiet'}`}
                            onClick={() => requestDelete(comment.id)}
                          >
                            {confirmDeleteId === comment.id ? 'Remove — sure?' : 'Remove'}
                          </button>
                        </div>
                      </>
                    )}
                  </div>
                </li>
              ))}
            </ul>

            <form className="comment-compose" onSubmit={handlePost}>
              <textarea
                className="textarea"
                placeholder="Add a comment…"
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                rows={2}
                aria-label="New comment"
              />
              <div className="comment-compose__row">
                {/* The server takes the author from the token, so there is no choice to offer. */}
                <span>
                  as <strong>{session.username}</strong>
                </span>
                <button type="submit" className="button" disabled={draft.trim() === ''}>
                  Comment
                </button>
              </div>
            </form>
          </section>

          {/* Under the discussion: not what people said about the card, but what happened to it.
              Read-only — the rows are written by the move itself. */}
          <section className="card">
            <h2 className="card__title">History</h2>

            {history !== null && history.length === 0 && (
              <p className="empty">Nobody has moved this card yet.</p>
            )}

            <ol className="history">
              {history?.map((move) => (
                <li className="history__row" key={move.id}>
                  <span className={STATUS_PILL[move.fromStatus]}>{STATUS_LABEL[move.fromStatus]}</span>
                  <span className="history__arrow" aria-hidden="true">
                    →
                  </span>
                  <span className={STATUS_PILL[move.toStatus]}>{STATUS_LABEL[move.toStatus]}</span>
                  {/* The name is on the row, not looked up in the members: whoever moved the card
                      may have left the project since, and "user 7" is not an answer. */}
                  <span>
                    by <strong>{move.changedByUsername}</strong>
                  </span>
                  <span className="history__time">
                    {timeFormatter.format(new Date(move.changedAt))}
                  </span>
                </li>
              ))}
            </ol>
          </section>
        </div>

        <aside className="side">
          <section className="card">
            <p className="eyebrow">Status</p>
            <p className="gap-top">
              <span className={`${STATUS_PILL[issue.status]} pill--status`}>
                {STATUS_LABEL[issue.status]}
              </span>
            </p>
            <p className="side__hint">Changes when the card is dragged on the board.</p>
          </section>

          <section className="card">
            <label className="eyebrow" htmlFor="assignee">
              Assigned to
            </label>
            <select
              id="assignee"
              className="select select--sm gap-top"
              value={issue.assigneeId ?? ''}
              onChange={(event) => void handleAssign(event.target.value)}
            >
              <option value="">Unassigned</option>
              {activeMembers.map((member) => (
                <option key={member.userId} value={member.userId}>
                  {member.username}
                </option>
              ))}
            </select>

            <div className="divider" />

            <p className="eyebrow">Reporter</p>
            <p className="person gap-top">
              <Avatar name={nameOf(issue.reporterId)} />
              {nameOf(issue.reporterId)}
            </p>
          </section>

          <section className="card">
            <dl className="facts">
              <dt>Type</dt>
              <dd>{capitalise(issue.type)}</dd>
              <dt>Priority</dt>
              <dd className={issue.priority === 'HIGH' || issue.priority === 'CRITICAL' ? 'facts__high' : ''}>
                {capitalise(issue.priority)}
              </dd>
              <dt>Due date</dt>
              <dd>{issue.dueDate ? dayFormatter.format(new Date(issue.dueDate)) : '—'}</dd>
              <dt>Created</dt>
              <dd>{dayFormatter.format(new Date(issue.createdAt))}</dd>
            </dl>
          </section>

          {/* What the AI made of this ticket. It arrives over a queue seconds after the issue is
              created, so this polls rather than expecting it to be there already. */}
          <SuggestionChip issueId={issue.id} onIssueChanged={() => void reloadIssue()} />
        </aside>
      </div>
    </Page>
  )
}
