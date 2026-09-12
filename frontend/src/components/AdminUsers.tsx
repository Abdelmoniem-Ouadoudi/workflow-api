import { useCallback, useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { AccountStatus, AdminAccount, Role } from '../api/types'
import { ROLES, ROLE_LABEL } from '../api/types'
import { useCurrentSession } from '../auth/SessionContext'
import { Notice } from './Notice'
import type { NoticeState } from './Notice'
import { Avatar, Page, PageHead } from './ui'

const STATUS_PILL: Record<AccountStatus, string> = {
  PENDING: 'pill pill--round',
  ACTIVE: 'pill pill--round pill--green',
  DISABLED: 'pill pill--round pill--red',
}

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
    <Page crumbs={[{ label: 'Administration' }, { label: 'Accounts' }]}>
      <PageHead title="Accounts" sub="Approve new people and decide what they can do." />
      <Notice notice={notice} onDismiss={() => setNotice(null)} />

      {loading ? (
        <p className="loading">Loading…</p>
      ) : (
        <div className="stack">
          <section className="card">
            <h2 className="card__title">
              Waiting for approval{pending.length > 0 ? ` (${pending.length})` : ''}
            </h2>
            {pending.length === 0 ? (
              <p className="note">Nobody is waiting.</p>
            ) : (
              <ul className="rows">
                {pending.map((account) => (
                  <li className="row" key={account.id}>
                    <div className="row__who">
                      <Avatar name={account.username} size="md" />
                      <div>
                        <p className="row__name">{account.username}</p>
                        <p className="row__sub">{account.email ?? 'no profile found'}</p>
                      </div>
                    </div>
                    <div className="row__actions">
                      {/*
                        The role is chosen at the moment of approving, and has no default beyond the
                        first option. Approving is when the decision is actually made; a pre-filled
                        answer would let somebody click through the queue without ever choosing.
                      */}
                      <select
                        className="select select--sm"
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
                        className="button button--sm"
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
                        className="button button--ghost button--sm"
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
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="card">
            <h2 className="card__title">Everybody else</h2>
            <ul className="rows">
              {decided.map((account) => {
                // An administrator who disabled or demoted their own account would be locked out
                // of the only screen that could undo it, and nobody else may open it.
                const isMe = account.workUserId === me.userId

                return (
                  <li className="row" key={account.id}>
                    <div className="row__who">
                      <Avatar name={account.username} size="md" />
                      <div>
                        <p className="row__name">
                          {account.username}
                          {isMe && ' (you)'}
                        </p>
                        <p className="row__sub">{account.email ?? 'no profile found'}</p>
                      </div>
                    </div>
                    <span className={STATUS_PILL[account.status]}>
                      {account.status.toLowerCase()}
                    </span>
                    <div className="row__actions">
                      <select
                        className="select select--sm"
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
                          className="button button--ghost button--sm"
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
                          className="button button--sm"
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
                  </li>
                )
              })}
            </ul>
          </section>

          {/*
            Said out loud rather than left to be discovered: a role change does not reach into a
            token that has already been issued. There is no revocation list - docs/BACKLOG.md.
          */}
          <p className="note">
            A role change takes effect the next time that person signs in. Their current session
            keeps the old role until it expires, within the hour.
          </p>
        </div>
      )}
    </Page>
  )
}
