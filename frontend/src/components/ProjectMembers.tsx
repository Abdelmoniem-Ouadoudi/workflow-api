import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import type { JoinRequest, ProjectMember, ProjectRole } from '../api/types'
import { PROJECT_ROLE_LABEL } from '../api/types'
import { useCurrentSession } from '../auth/SessionContext'
import { Notice } from './Notice'
import type { NoticeState } from './Notice'

/**
 * The chef de projet's screen: this project's people, its join code, and who is asking to get in.
 *
 * Everything here is scoped to one project by design. A project manager runs the project they
 * created and has no reach into any other — which is what keeps "project manager" from quietly
 * meaning "administrator".
 */
export function ProjectMembers() {
  const projectId = Number(useParams<{ id: string }>().id)
  const me = useCurrentSession()

  const [members, setMembers] = useState<ProjectMember[]>([])
  const [requests, setRequests] = useState<JoinRequest[]>([])
  const [joinCode, setJoinCode] = useState<string | null>(null)
  const [notice, setNotice] = useState<NoticeState | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)

  // A member sees the list; only a manager sees the code and the queue. Rather than reading the
  // role from somewhere, the page asks for the manager-only things and treats a 403 as the answer.
  // One source of truth — the server — instead of a guess in the browser that could disagree.
  const manages = joinCode !== null

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setMembers(await api.projectMembers(projectId))

      try {
        const [code, pending] = await Promise.all([
          api.joinCode(projectId),
          api.projectJoinRequests(projectId),
        ])
        setJoinCode(code.joinCode)
        setRequests(pending)
      } catch (error) {
        // Not a manager of this project. The member list still renders; the rest does not exist
        // for them, which is the same answer the server would give to every button on it.
        if (!(error instanceof ApiError) || error.status !== 403) throw error
        setJoinCode(null)
        setRequests([])
      }
    } catch (error) {
      if (error instanceof ApiError) setNotice({ tone: 'stop', message: error.message })
    } finally {
      setLoading(false)
    }
  }, [projectId])

  useEffect(() => {
    void load()
  }, [load])

  async function act(action: () => Promise<unknown>, done: string) {
    setNotice(null)
    setBusy(true)
    try {
      await action()
      setNotice({ tone: 'note', message: done })
      await load()
    } catch (error) {
      if (error instanceof ApiError) setNotice({ tone: 'stop', message: error.message })
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="page">
      <header className="masthead">
        <div>
          <p className="masthead__eyebrow">Project</p>
          <h1 className="masthead__title">People on this project</h1>
        </div>
        <div className="chip__actions">
          <Link className="button button--quiet" to={`/projects/${projectId}/board`}>
            Board
          </Link>
          <Link className="button button--quiet" to="/projects">
            Projects
          </Link>
        </div>
      </header>
      <div className="masthead__rail" aria-hidden="true" />

      <Notice notice={notice} onDismiss={() => setNotice(null)} />

      {loading ? (
        <p className="page__loading">Loading…</p>
      ) : (
        <>
          {manages && (
            <section className="projects">
              <h2 className="masthead__title">Join code</h2>
              <div className="projects__row">
                <code className="chip">{joinCode}</code>
                <div className="chip__actions">
                  <button
                    className="button button--quiet"
                    type="button"
                    onClick={() => void navigator.clipboard?.writeText(joinCode ?? '')}
                  >
                    Copy
                  </button>
                  <button
                    className="button button--quiet"
                    type="button"
                    disabled={busy}
                    onClick={() =>
                      void act(
                        () => api.rotateJoinCode(projectId),
                        'New code. The old one no longer works.',
                      )
                    }
                  >
                    Replace it
                  </button>
                </div>
              </div>
              {/*
                Both halves stated, because "rotate" sounds more destructive than it is and a
                manager who is unsure will never use it.
              */}
              <p className="gate__hint">
                Send this to somebody you want on the project. Replacing it stops new people using
                the old one — it does not remove anybody who is already here, and it does not
                cancel a request that is already waiting below.
              </p>
              <p className="gate__hint">
                This is not the project key printed on tickets. That one is public; this one is not.
              </p>
            </section>
          )}

          {manages && (
            <section className="projects">
              <h2 className="masthead__title">
                Asking to join{requests.length > 0 ? ` (${requests.length})` : ''}
              </h2>
              {requests.length === 0 ? (
                <p className="gate__hint">Nobody is waiting.</p>
              ) : (
                requests.map((request) => (
                  <div className="projects__row" key={request.id}>
                    <div>
                      <strong>{request.username}</strong>
                      <span className="chip">{request.email}</span>
                    </div>
                    <div className="chip__actions">
                      <button
                        className="button"
                        type="button"
                        disabled={busy}
                        onClick={() =>
                          void act(
                            () => api.approveJoinRequest(projectId, request.id),
                            `${request.username} is on the project.`,
                          )
                        }
                      >
                        Accept
                      </button>
                      <button
                        className="button button--quiet"
                        type="button"
                        disabled={busy}
                        onClick={() =>
                          void act(
                            () => api.rejectJoinRequest(projectId, request.id),
                            `${request.username} was refused.`,
                          )
                        }
                      >
                        Refuse
                      </button>
                    </div>
                  </div>
                ))
              )}
            </section>
          )}

          <section className="projects">
            <h2 className="masthead__title">Members</h2>
            {members.map((member) => (
              <div className="projects__row" key={member.userId}>
                <div>
                  <strong>{member.username}</strong>
                  <span className="chip">{member.email}</span>
                  <span className="chip">{PROJECT_ROLE_LABEL[member.role]}</span>
                  {member.userId === me.userId && <span className="chip">you</span>}
                </div>
                {manages && (
                  <div className="chip__actions">
                    <select
                      className="compose__select"
                      aria-label={`Role of ${member.username} on this project`}
                      value={member.role}
                      disabled={busy}
                      onChange={(event) =>
                        void act(
                          () =>
                            api.changeProjectMemberRole(
                              projectId,
                              member.userId,
                              event.target.value as ProjectRole,
                            ),
                          `${member.username} is now a ${event.target.value === 'PROJECT_MANAGER' ? 'project manager' : 'member'}.`,
                        )
                      }
                    >
                      <option value="MEMBER">{PROJECT_ROLE_LABEL.MEMBER}</option>
                      <option value="PROJECT_MANAGER">{PROJECT_ROLE_LABEL.PROJECT_MANAGER}</option>
                    </select>
                    <button
                      className="button button--quiet"
                      type="button"
                      disabled={busy}
                      onClick={() =>
                        void act(
                          () => api.removeProjectMember(projectId, member.userId),
                          `${member.username} was removed.`,
                        )
                      }
                    >
                      Remove
                    </button>
                  </div>
                )}
              </div>
            ))}
            {manages && (
              // The rule the database cannot hold, said before somebody hits it: a project with
              // no manager still works for everybody on it, and cannot gain another member.
              <p className="gate__hint">
                A project always keeps at least one project manager. Promote somebody else before
                standing down.
              </p>
            )}
          </section>
        </>
      )}
    </main>
  )
}
