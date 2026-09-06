import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import type { BoardView, IssueSummary, Project, Status, User } from '../api/types'
import { STATUSES } from '../api/types'
import { canMove, holdReason } from '../api/workflow'
import { useCurrentSession } from '../auth/SessionContext'
import { Column } from './Column'
import type { ColumnState } from './Column'
import { IssueDetail } from './IssueDetail'
import { NewIssueForm } from './NewIssueForm'
import { Notice } from './Notice'
import type { NoticeState } from './Notice'

interface Lift {
  id: number
  from: Status
  version: number
}

/**
 * The board for one project, now reached by URL.
 *
 * It loads the project from the id in the path rather than being handed one, which is what makes
 * a board bookmarkable and linkable. It is also where the scoping shows: opening
 * `/projects/9/board` for a project you are not on gets a 403 from work-service, not an empty
 * board, and the notice says so.
 */
export function Board() {
  const projectId = Number(useParams<{ id: string }>().id)
  const session = useCurrentSession()

  const [project, setProject] = useState<Project | null>(null)
  const [view, setView] = useState<BoardView | null>(null)
  const [users, setUsers] = useState<User[]>([])
  const [lift, setLift] = useState<Lift | null>(null)
  const [notice, setNotice] = useState<NoticeState | null>(null)
  const [loading, setLoading] = useState(true)
  const [openIssueId, setOpenIssueId] = useState<number | null>(null)

  const load = useCallback(async () => {
    const boards = await api.listBoards(projectId)
    if (boards.length === 0) throw new ApiError({
      timestamp: new Date().toISOString(),
      status: 404,
      code: 'NO_BOARD',
      message: 'This project has no board.',
      path: `/boards?projectId=${projectId}`,
    })
    setView(await api.boardView(boards[0].id))
  }, [projectId])

  useEffect(() => {
    let cancelled = false
    async function start() {
      setLoading(true)
      try {
        // The assignee list is the project's members, not every user in the system. Two reasons:
        // GET /users hands out everybody's email and is ADMIN-only since M5, and work can only be
        // given to somebody who is on the project - work-service refuses the rest with 422.
        const [opened, , members] = await Promise.all([
          api.getProject(projectId),
          load(),
          api.projectMembers(projectId),
        ])
        if (cancelled) return
        setProject(opened)
        setUsers(
          members
            .filter((member) => member.active)
            .map((member) => ({
              id: member.userId,
              username: member.username,
              email: member.email,
              role: member.globalRole,
              active: member.active,
              createdAt: member.joinedAt,
            })),
        )
      } catch (error) {
        if (!cancelled && error instanceof ApiError) {
          setNotice({ tone: 'stop', message: error.message })
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    void start()
    return () => {
      cancelled = true
    }
  }, [load, projectId])

  /** Moves a card between columns in local state so the board reacts before the server answers. */
  function relocate(current: BoardView, id: number, from: Status, to: Status): BoardView {
    const card = current.columns
      .find((column) => column.status === from)
      ?.issues.find((issue) => issue.id === id)
    if (!card) return current

    return {
      ...current,
      columns: current.columns.map((column) => {
        if (column.status === from) {
          return { ...column, issues: column.issues.filter((issue) => issue.id !== id) }
        }
        if (column.status === to) {
          return { ...column, issues: [...column.issues, { ...card, status: to }] }
        }
        return column
      }),
    }
  }

  function applyServerState(id: number, status: Status, version: number) {
    setView((current) =>
      current === null
        ? current
        : {
            ...current,
            columns: current.columns.map((column) => ({
              ...column,
              issues: column.issues.map((issue) =>
                issue.id === id ? { ...issue, status, version } : issue,
              ),
            })),
          },
    )
  }

  async function move(issueId: number, from: Status, to: Status, version: number) {
    if (from === to) return

    if (!canMove(from, to)) {
      setNotice({ tone: 'stop', message: holdReason(from, to) })
      return
    }

    const snapshot = view
    if (snapshot === null) return

    setView(relocate(snapshot, issueId, from, to))
    setNotice(null)

    try {
      const updated = await api.moveIssue(issueId, to, version)
      applyServerState(issueId, updated.status, updated.version)
    } catch (error) {
      setView(snapshot)
      if (!(error instanceof ApiError)) return

      if (error.code === 'STALE_VERSION') {
        setNotice({ tone: 'note', message: 'Someone else moved this card. Reloading the board.' })
        await load()
      } else {
        setNotice({ tone: 'stop', message: error.message })
      }
    }
  }

  function nudge(issue: IssueSummary, direction: -1 | 1) {
    const index = STATUSES.indexOf(issue.status) + direction
    if (index < 0 || index >= STATUSES.length) return
    void move(issue.id, issue.status, STATUSES[index], issue.version)
  }

  function applyAssignee(issueId: number, assigneeId: number | null, version: number) {
    const assigneeUsername = assigneeId === null
      ? null
      : (users.find((user) => user.id === assigneeId)?.username ?? null)

    setView((current) =>
      current === null
        ? current
        : {
            ...current,
            columns: current.columns.map((column) => ({
              ...column,
              issues: column.issues.map((issue) =>
                issue.id === issueId ? { ...issue, assigneeId, assigneeUsername, version } : issue,
              ),
            })),
          },
    )
  }

  const openIssue = openIssueId === null
    ? null
    : (view?.columns.flatMap((column) => column.issues).find((issue) => issue.id === openIssueId) ?? null)

  function columnState(status: Status): ColumnState {
    if (lift === null) return 'idle'
    if (lift.from === status) return 'source'
    return canMove(lift.from, status) ? 'clear' : 'held'
  }

  if (loading) {
    return <p className="page__loading">Loading the board…</p>
  }

  // A refused or missing project leaves nothing to draw a board around. The notice already says
  // why - "You are not a member of this project" comes straight from work-service.
  if (project === null) {
    return (
      <main className="page">
        <Notice notice={notice} onDismiss={() => setNotice(null)} />
        <Link className="button button--quiet" to="/projects">
          ← Projects
        </Link>
      </main>
    )
  }

  return (
    <main className="page">
      <Notice notice={notice} onDismiss={() => setNotice(null)} />

      <header className="panel">
        <Link className="panel__back" to="/projects">
          ← Projects
        </Link>
        <div className="panel__id">
          <span className="panel__key">{project.key}</span>
          <span className="panel__name">{view?.boardName ?? project.name}</span>
        </div>
        <Link className="button button--quiet" to={`/projects/${projectId}/members`}>
          People
        </Link>
        <span className="panel__mode">{view?.type.toLowerCase()}</span>
      </header>

      {view && (
        <NewIssueForm
          projectId={project.id}
          projectKey={project.key}
          boardId={view.boardId}
          onCreated={() => void load()}
          onFailed={(message) => setNotice({ tone: 'stop', message })}
          // The duplicate panel found the ticket being retyped. Opening it is the whole point:
          // the best outcome of filing an issue is not filing it.
          onOpenExisting={setOpenIssueId}
        />
      )}

      <div className={`board${lift ? ' board--lifting' : ''}`}>
        {view?.columns.map((column, index) => (
          <Column
            key={column.status}
            index={index}
            status={column.status}
            issues={column.issues}
            state={columnState(column.status)}
            holdReason={lift ? holdReason(lift.from, column.status) : null}
            liftedId={lift?.id ?? null}
            onLift={(issue) =>
              setLift({ id: issue.id, from: issue.status, version: issue.version })
            }
            onLiftEnd={() => setLift(null)}
            onDropHere={() => {
              const held = lift
              setLift(null)
              if (held) void move(held.id, held.from, column.status, held.version)
            }}
            onNudge={nudge}
            onOpen={(issue) => setOpenIssueId(issue.id)}
          />
        ))}
      </div>

      {openIssue && (
        <IssueDetail
          issue={openIssue}
          users={users}
          session={session}
          onClose={() => setOpenIssueId(null)}
          onAssigneeChanged={(assigneeId, version) => applyAssignee(openIssue.id, assigneeId, version)}
          onAssignFailed={(message) => setNotice({ tone: 'stop', message })}
          // Accepting a suggestion writes the AI's values onto the issue, which bumps its
          // version. Refetching is what stops the next drag on that card sending a stale one
          // and getting a false 409.
          onIssueChanged={() => void load()}
        />
      )}
    </main>
  )
}
