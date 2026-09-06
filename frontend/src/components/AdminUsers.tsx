import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import type { AdminAccount, Role } from '../api/types'
import { ROLES, ROLE_LABEL } from '../api/types'
import { useCurrentSession } from '../auth/SessionContext'
import { Notice } from './Notice'
import type { NoticeState } from './Notice'

/**
 * The administrator's console: who exists, what they are, and whether they may sign in.
 *
 * Note what is not here — putting people on projects. That is the project manager's screen, and
 * deliberately so: an administrator who had to assign every person to every project would be the
 * bottleneck the project role exists to remove. This page decides who exists at all.
 */
export function AdminUsers() {
  const me = useCurrentSession()
  const [accounts, setAccounts] = useState<AdminAccount[]>([])
  const [roleChoice, setRoleChoice] = useState<Record<number, Role>>({})
  const [notice, setNotice] = useState<NoticeState | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState<number | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setAccounts(await api.adminAccounts())
    } catch (error) {
      if (error instanceof ApiError) setNotice({ tone: 'stop', message: error.message })
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  async function act(id: number, action: () => Promise<unknown>, done: string) {
    setNotice(null)
    setBusy(id)
    try {
      await action()
      setNotice({ tone: 'note', message: done })
      await load()
    } catch (error) {
      if (error instanceof ApiError) setNotice({ tone: 'stop', message: error.message })
    } finally {
      setBusy(null)
    }
  }

  const pending = accounts.filter((account) => account.status === 'PENDING')
  const decided = accounts.filter((account) => account.status !== 'PENDING')

  return (
    <main className="page">
      <header className="masthead">
        <div>
          <p className="masthead__eyebrow">Administration</p>
          <h1 className="masthead__title">People</h1>
        </div>
        <Link className="button button--quiet" to="/projects">
          Projects
        </Link>
      </header>
      <div className="masthead__rail" aria-hidden="true" />

      <Notice notice={notice} onDismiss={() => setNotice(null)} />

      {loading ? (
        <p className="page__loading">Loading…</p>
      ) : (
        <>
          <section className="projects">
            <h2 className="masthead__title">
              Waiting for approval{pending.length > 0 ? ` (${pending.length})` : ''}
            </h2>
            {pending.length === 0 ? (
              <p className="gate__hint">Nobody is waiting.</p>
            ) : (
              pending.map((account) => (
                <div className="projects__row" key={account.id}>
                  <div>
                    <strong>{account.username}</strong>
                    <span className="chip">{account.email ?? 'no profile found'}</span>
                  </div>
                  <div className="chip__actions">
                    {/*
                      The role is chosen at the moment of approving, and has no default beyond the
                      first option. Approving is when the decision is actually made; a pre-filled
                      answer would let somebody click through the queue without ever choosing.
                    */}
                    <select
                      className="compose__select"
                      aria-label={`Role for ${account.username}`}
                      value={roleChoice[account.id] ?? 'DEVELOPER'}
                      onChange={(event) =>
                        setRoleChoice({ ...roleChoice, [account.id]: event.target.value as Role })
                      }
                    >
                      {ROLES.map((role) => (
                        <option key={role} value={role}>
                          {ROLE_LABEL[role]}
                        </option>
                      ))}
                    </select>
                    <button
                      className="button"
                      type="button"
                      disabled={busy === account.id}
                      onClick={() =>
                        void act(
                          account.id,
                          () =>
                            api.approveAccount(account.id, roleChoice[account.id] ?? 'DEVELOPER'),
                          `${account.username} can now sign in.`,
                        )
                      }
                    >
                      Approve
                    </button>
                    <button
                      className="button button--quiet"
                      type="button"
                      disabled={busy === account.id}
                      onClick={() =>
                        void act(
                          account.id,
                          () => api.rejectAccount(account.id),
                          `${account.username} was refused.`,
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

          <section className="projects">
            <h2 className="masthead__title">Everybody else</h2>
            {decided.map((account) => {
              // An administrator who disabled or demoted their own account would be locked out of
              // the only screen that could undo it, and nobody else may open it.
              const isMe = account.workUserId === me.userId

              return (
                <div className="projects__row" key={account.id}>
                  <div>
                    <strong>{account.username}</strong>
                    <span className="chip">{account.email ?? 'no profile found'}</span>
                    <span className="chip">{account.status.toLowerCase()}</span>
                    {isMe && <span className="chip">you</span>}
                  </div>
                  <div className="chip__actions">
                    <select
                      className="compose__select"
                      aria-label={`Role for ${account.username}`}
                      value={account.role}
                      disabled={isMe || busy === account.id}
                      onChange={(event) =>
                        void act(
                          account.id,
                          () => api.changeAccountRole(account.id, event.target.value as Role),
                          `${account.username} is now a ${event.target.value.toLowerCase()}.`,
                        )
                      }
                    >
                      {ROLES.map((role) => (
                        <option key={role} value={role}>
                          {ROLE_LABEL[role]}
                        </option>
                      ))}
                    </select>
                    {account.status === 'ACTIVE' ? (
                      <button
                        className="button button--quiet"
                        type="button"
                        disabled={isMe || busy === account.id}
                        onClick={() =>
                          void act(
                            account.id,
                            () => api.disableAccount(account.id),
                            `${account.username} can no longer sign in.`,
                          )
                        }
                      >
                        Disable
                      </button>
                    ) : (
                      <button
                        className="button"
                        type="button"
                        disabled={busy === account.id}
                        onClick={() =>
                          void act(
                            account.id,
                            () => api.enableAccount(account.id),
                            `${account.username} can sign in again.`,
                          )
                        }
                      >
                        Enable
                      </button>
                    )}
                  </div>
                </div>
              )
            })}
          </section>

          {/*
            Said out loud rather than left to be discovered: a role change does not reach into a
            token that has already been issued. There is no revocation list - docs/BACKLOG.md.
          */}
          <p className="gate__hint">
            A role change takes effect the next time that person signs in. Their current session
            keeps the old role until it expires, within the hour.
          </p>
        </>
      )}
    </main>
  )
}
