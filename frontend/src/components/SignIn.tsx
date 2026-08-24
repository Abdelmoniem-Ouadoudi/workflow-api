import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import type { Role } from '../api/types'
import type { Session } from '../auth/session'
import { Notice } from './Notice'
import type { NoticeState } from './Notice'

interface Props {
  onSignedIn: (session: Session) => void
}

type Mode = 'signIn' | 'createAccount'

/** Same three the backend accepts. Shown in the order of how much they can do. */
const ROLES: Role[] = ['DEVELOPER', 'MANAGER', 'ADMIN']

const ROLE_LABEL: Record<Role, string> = {
  DEVELOPER: 'Developer',
  MANAGER: 'Manager',
  ADMIN: 'Admin',
}

/**
 * One screen, two modes. Signing in and creating an account share every field but two, and
 * splitting them across two screens would mean two layouts to keep in step for no gain.
 */
export function SignIn({ onSignedIn }: Props) {
  const [mode, setMode] = useState<Mode>('signIn')
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<Role>('DEVELOPER')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [notice, setNotice] = useState<NoticeState | null>(null)
  const [busy, setBusy] = useState(false)

  const creating = mode === 'createAccount'

  function switchTo(next: Mode) {
    setMode(next)
    // The messages belonged to the other mode. Leaving them would have the form complain
    // about a field it is no longer showing.
    setErrors({})
    setNotice(null)
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setErrors({})
    setNotice(null)
    setBusy(true)

    try {
      const session = creating
        ? await api.register({ username, email, password, role })
        : await api.login({ username, password })
      onSignedIn(session)
    } catch (error) {
      if (!(error instanceof ApiError)) return
      if (error.fieldErrors.length > 0) {
        setErrors(Object.fromEntries(error.fieldErrors.map((e) => [e.field, e.message])))
      } else {
        setNotice({ tone: 'stop', message: error.message })
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="gate">
      <div className="gate__panel">
        <Notice notice={notice} onDismiss={() => setNotice(null)} />

        <header className="gate__head">
          <p className="masthead__eyebrow">Access</p>
          <h1 className="gate__title">Workflow</h1>
          <p className="gate__sub">
            {creating
              ? 'Create an account to start tracking work.'
              : 'Sign in to reach your projects.'}
          </p>
          <div className="masthead__rail" aria-hidden="true" />
        </header>

        <div className="gate__modes" role="tablist" aria-label="Access mode">
          <button
            type="button"
            role="tab"
            aria-selected={!creating}
            className={`gate__mode${!creating ? ' gate__mode--on' : ''}`}
            onClick={() => switchTo('signIn')}
          >
            Sign in
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={creating}
            className={`gate__mode${creating ? ' gate__mode--on' : ''}`}
            onClick={() => switchTo('createAccount')}
          >
            Create account
          </button>
        </div>

        <form className="gate__form" onSubmit={handleSubmit}>
          <div className="compose__field">
            <label className="gate__label" htmlFor="username">
              Username
            </label>
            <input
              id="username"
              className={`compose__input${errors.username ? ' compose__input--bad' : ''}`}
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              autoComplete="username"
              aria-invalid={Boolean(errors.username)}
            />
            {errors.username && <span className="compose__error">{errors.username}</span>}
          </div>

          {creating && (
            <div className="compose__field">
              <label className="gate__label" htmlFor="email">
                Email
              </label>
              <input
                id="email"
                type="email"
                className={`compose__input${errors.email ? ' compose__input--bad' : ''}`}
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                autoComplete="email"
                aria-invalid={Boolean(errors.email)}
              />
              {errors.email && <span className="compose__error">{errors.email}</span>}
            </div>
          )}

          <div className="compose__field">
            <label className="gate__label" htmlFor="password">
              Password
            </label>
            <input
              id="password"
              type="password"
              className={`compose__input${errors.password ? ' compose__input--bad' : ''}`}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete={creating ? 'new-password' : 'current-password'}
              aria-invalid={Boolean(errors.password)}
            />
            {errors.password && <span className="compose__error">{errors.password}</span>}
            {creating && !errors.password && (
              <span className="gate__hint">At least 8 characters.</span>
            )}
          </div>

          {creating && (
            <div className="compose__field">
              <label className="gate__label" htmlFor="role">
                Role
              </label>
              <select
                id="role"
                className="compose__select gate__select"
                value={role}
                onChange={(event) => setRole(event.target.value as Role)}
              >
                {ROLES.map((value) => (
                  <option key={value} value={value}>
                    {ROLE_LABEL[value]}
                  </option>
                ))}
              </select>
              {/* Stated rather than discovered: the one rule the role currently decides. */}
              <span className="gate__hint">Only an admin can deactivate a user.</span>
            </div>
          )}

          <button className="button gate__submit" type="submit" disabled={busy}>
            {busy ? 'Working…' : creating ? 'Create account' : 'Sign in'}
          </button>
        </form>
      </div>
    </main>
  )
}
