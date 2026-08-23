import { useCallback, useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { BoardView, IssueSummary, Project, Status, User } from '../api/types'
import { STATUSES } from '../api/types'
import { canMove, holdReason } from '../api/workflow'
import { Column } from './Column'
import type { ColumnState } from './Column'
import { NewIssueForm } from './NewIssueForm'
import { Notice } from './Notice'
import type { NoticeState } from './Notice'

interface Lift {
  id: number
  from: Status
  version: number
}

interface Props {
  project: Project
  onLeave: () => void
}

export function Board({ project, onLeave }: Props) {
  const [view, setView] = useState<BoardView | null>(null)
  const [users, setUsers] = useState<User[]>([])
  const [lift, setLift] = useState<Lift | null>(null)
  const [notice, setNotice] = useState<NoticeState | null>(null)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    const boards = await api.listBoards(project.id)
    if (boards.length === 0) throw new ApiError({
      timestamp: new Date().toISOString(),
      status: 404,
      code: 'NO_BOARD',
      message: 'This project has no board.',
      path: `/boards?projectId=${project.id}`,
    })
    setView(await api.boardView(boards[0].id))
  }, [project.id])

  useEffect(() => {
    let cancelled = false
    async function start() {
      setLoading(true)
      try {
        const [, people] = await Promise.all([load(), api.listUsers()])
        if (!cancelled) setUsers(people.filter((user) => user.active))
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
  }, [load])

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

  function columnState(status: Status): ColumnState {
    if (lift === null) return 'idle'
    if (lift.from === status) return 'source'
    return canMove(lift.from, status) ? 'clear' : 'held'
  }

  if (loading) {
    return <p className="page__loading">Loading the board…</p>
  }

  return (
    <main className="page">
      <Notice notice={notice} onDismiss={() => setNotice(null)} />

      <header className="panel">
        <button type="button" className="panel__back" onClick={onLeave}>
          ← Projects
        </button>
        <div className="panel__id">
          <span className="panel__key">{project.key}</span>
          <span className="panel__name">{view?.boardName ?? project.name}</span>
        </div>
        <span className="panel__mode">{view?.type.toLowerCase()}</span>
      </header>

      {view && (
        <NewIssueForm
          projectId={project.id}
          boardId={view.boardId}
          users={users}
          onCreated={() => void load()}
          onFailed={(message) => setNotice({ tone: 'stop', message })}
        />
      )}

      <div className={`board${lift ? ' board--lifting' : ''}`}>
        {view?.columns.map((column) => (
          <Column
            key={column.status}
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
          />
        ))}
      </div>
    </main>
  )
}
