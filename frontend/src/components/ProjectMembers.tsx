import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import type { JoinRequest, Project, ProjectMember, ProjectRole } from '../api/types'
import { PROJECT_ROLE_LABEL } from '../api/types'
import { useCurrentSession } from '../auth/SessionContext'
import { Notice } from './Notice'
import type { NoticeState } from './Notice'
import { Avatar, Page, PageHead } from './ui'

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

  const [project, setProject] = useState<Project | null>(null)
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
      const [opened, people] = await Promise.all([
        api.getProject(projectId),
        api.projectMembers(projectId),
      ])
      setProject(opened)
      setMembers(people)

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
    <Page crumbs={[{ label: project?.name ?? 'Project', to: `/projects/${projectId}/board` }, { label: 'People' }]}>
      <PageHead title="People" sub="Who is on this project, and who is asking to join." />
      <Notice notice={notice} onDismiss={() => setNotice(null)} />

      {loading ? (
        <p className="loading">Loading…</p>
      ) : (
        <div className="stack">
          {manages && (
            <section className="card">
              <div className="card__head">
                <h2 className="card__title">Join code</h2>
                <div className="row__actions">
                  <button
                    className="button button--ghost button--sm"
                    type="button"
                    onClick={() => void navigator.clipboard?.writeText(joinCode ?? '')}
                  >
                    Copy
                  </button>
                  <button
                    className="button button--ghost button--sm"
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
              <code className="code">{joinCode}</code>
              {/*
                Both halves stated, because "rotate" sounds more destructive than it is and a
                manager who is unsure will never use it.
              */}
              <p className="note gap-top">
                Send this to somebody you want on the project. Replacing it stops new people using
                the old one — it does not remove anybody who is already here, and it does not
                cancel a request that is already waiting below. It is not the project key printed
                on tickets: that one is public, this one is not.
              </p>
            </section>
          )}

          {manages && (
            <section className="card">
              <h2 className="card__title">
                Asking to join{requests.length > 0 ? ` (${requests.length})` : ''}
              </h2>
              {requests.length === 0 ? (
                <p className="note">Nobody is waiting.</p>
              ) : (
                <ul className="rows">
                  {requests.map((request) => (
                    <li className="row" key={request.id}>
                      <div className="row__who">
                        <Avatar name={request.username} size="md" />
                        <div>
                          <p className="row__name">{request.username}</p>
                          <p className="row__sub">{request.email}</p>
                        </div>
                      </div>
                      <div className="row__actions">
                        <button
                          className="button button--sm"
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
                          className="button button--ghost button--sm"
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
                    </li>
                  ))}
                </ul>
              )}
            </section>
          )}

          <section className="card">
            <h2 className="card__title">Team members</h2>
            <ul className="rows">
              {members.map((member) => (
                <li className="row" key={member.userId}>
                  <div className="row__who">
                    <Avatar name={member.username} size="md" />
                    <div>
                      <p className="row__name">
                        {member.username}
                        {member.userId === me.userId && ' (you)'}
                      </p>
                      <p className="row__sub">{member.email}</p>
                    </div>
                  </div>

                  {manages ? (
                    <div className="row__actions">
                      <select
                        className="select select--sm"
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
                        className="button button--ghost button--sm"
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
                  ) : (
                    <span
                      className={`pill pill--round ${member.role === 'PROJECT_MANAGER' ? 'pill--red' : 'pill--teal'}`}
                    >
                      {PROJECT_ROLE_LABEL[member.role]}
                    </span>
                  )}
                </li>
              ))}
            </ul>
            {manages && (
              // The rule the database cannot hold, said before somebody hits it: a project with
              // no manager still works for everybody on it, and cannot gain another member.
              <p className="note gap-top">
                A project always keeps at least one project manager. Promote somebody else before
                standing down.
              </p>
            )}
          </section>
        </div>
      )}
    </Page>
  )
}
