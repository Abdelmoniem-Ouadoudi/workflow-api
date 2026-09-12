import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import type { BoardView, IssueSummary, Project, Status } from '../api/types'
import { STATUSES } from '../api/types'
import { canMove, holdReason } from '../api/workflow'
import { Column } from './Column'
import type { ColumnState } from './Column'
import { NEW_ISSUE_TITLE_ID, NewIssueForm } from './NewIssueForm'
import { Notice } from './Notice'
import type { NoticeState } from './Notice'
import { Page, PageHead } from './ui'

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
 *
 * Opening a card goes to the issue's own page. Coming back reloads the board, so a card whose
 * version moved while it was open — assigned, or changed by an accepted AI suggestion — is never
 * dragged with a stale one.
 */
export function Board() {
  const projectId = Number(useParams<{ id: string }>().id)
  const navigate = useNavigate()

  const [project, setProject] = useState<Project | null>(null)
  const [view, setView] = useState<BoardView | null>(null)
  const [lift, setLift] = useState<Lift | null>(null)
  const [notice, setNotice] = useState<NoticeState | null>(null)
  const [loading, setLoading] = useState(true)

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
        const [opened] = await Promise.all([api.getProject(projectId), load()])
        if (!cancelled) setProject(opened)
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

  function openIssue(issueId: number) {
    navigate(`/projects/${projectId}/issues/${issueId}`)
  }

  function columnState(status: Status): ColumnState {
    if (lift === null) return 'idle'
    if (lift.from === status) return 'source'
    return canMove(lift.from, status) ? 'clear' : 'held'
  }

  if (loading) {
    return (
      <Page crumbs={[{ label: 'Board' }]}>
        <p className="loading">Loading the board…</p>
      </Page>
    )
  }

  // A refused or missing project leaves nothing to draw a board around. The notice already says
  // why - "You are not a member of this project" comes straight from work-service.
  if (project === null) {
    return (
      <Page crumbs={[{ label: 'Board' }]}>
        <Notice notice={notice} onDismiss={() => setNotice(null)} />
        <Link className="button button--ghost" to="/projects">
          ← Projects
        </Link>
      </Page>
    )
  }

  const boardType = view ? view.type.charAt(0) + view.type.slice(1).toLowerCase() : ''
  const sprint = view?.activeSprint ?? null

  return (
    <Page
      crumbs={[{ label: project.name }, { label: 'Board' }]}
      actions={
        <button
          type="button"
          className="button"
          onClick={() => document.getElementById(NEW_ISSUE_TITLE_ID)?.focus()}
        >
          + Create issue
        </button>
      }
    >
      <PageHead
        title={view?.boardName ?? project.name}
        sub={
          <>
            {project.key} · {boardType} board
            {sprint?.goal && <> · Sprint goal: {sprint.goal}</>}
          </>
        }
        aside={
          sprint && <span className="pill pill--blue pill--round">● {sprint.name} · Active</span>
        }
      />

      <Notice notice={notice} onDismiss={() => setNotice(null)} />

      <div className="board-layout">
        <div className="board">
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
              onOpen={(issue) => openIssue(issue.id)}
            />
          ))}
        </div>

        {view && (
          <NewIssueForm
            projectId={project.id}
            projectKey={project.key}
            boardId={view.boardId}
            onCreated={() => void load()}
            onFailed={(message) => setNotice({ tone: 'stop', message })}
            // The duplicate panel found the ticket being retyped. Opening it is the whole point:
            // the best outcome of filing an issue is not filing it.
            onOpenExisting={openIssue}
          />
        )}
      </div>
    </Page>
  )
}
