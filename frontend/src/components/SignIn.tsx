import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import { useSession } from '../auth/SessionContext'
import { Notice } from './Notice'
import type { NoticeState } from './Notice'

interface Props {
  /** Set by the route: /login or /register. */
  mode: 'signIn' | 'createAccount'
}

interface LocationState {
  from?: { pathname: string }
}

/**
 * One component, two modes, now reached by two routes.
 *
 * The two modes differ by one field and one button, so splitting them into two files would mean
 * two layouts to keep in step. What changed at M5 is what each one *does*: signing in starts a
 * session, creating an account does not — it produces a request for an administrator.
 */
export function SignIn({ mode }: Props) {
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [notice, setNotice] = useState<NoticeState | null>(null)
  const [busy, setBusy] = useState(false)

  const { signIn } = useSession()
  const navigate = useNavigate()
  const location = useLocation()

  const creating = mode === 'createAccount'

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setErrors({})
    setNotice(null)
    setBusy(true)

    try {
      if (creating) {
        // No session, no token, nowhere to go but a page that explains why.
        await api.register({ username, email, password })
        navigate('/pending', { replace: true, state: { username } })
        return
      }

      signIn(await api.login({ username, password }))
      // Back to whatever they were trying to reach before the guard bounced them here — a mailed
      // /join/CODE link, most usefully. Falls back to the project list on a plain sign-in.
      const from = (location.state as LocationState | null)?.from?.pathname
      navigate(from ?? '/projects', { replace: true })
    } catch (error) {
      if (!(error instanceof ApiError)) return

      // A PENDING account is not a failed sign-in, it is a sign-in that is too early. Sending it
      // to the waiting page says so properly instead of showing a red banner over the password
      // field, which reads as "you typed it wrong".
      if (error.code === 'ACCOUNT_PENDING') {
        navigate('/pending', { replace: true, state: { username } })
        return
      }
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
              ? 'Create an account. An administrator approves it before you can sign in.'
              : 'Sign in to reach your projects.'}
          </p>
          <div className="masthead__rail" aria-hidden="true" />
        </header>

        <div className="gate__modes" role="tablist" aria-label="Access mode">
          <Link
            to="/login"
            role="tab"
            aria-selected={!creating}
            className={`gate__mode${!creating ? ' gate__mode--on' : ''}`}
          >
            Sign in
          </Link>
          <Link
            to="/register"
            role="tab"
            aria-selected={creating}
            className={`gate__mode${creating ? ' gate__mode--on' : ''}`}
          >
            Create account
          </Link>
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

          {/*
            There was a role dropdown here until M5, on a page anybody can open, which meant
            anyone could make themselves an administrator. Everybody now registers the same way
            and an administrator decides what they are.
          */}
          {creating && (
            <p className="gate__hint">
              An administrator reviews new accounts and decides what you can do. You will not be
              able to sign in until they have.
            </p>
          )}

          <button className="button gate__submit" type="submit" disabled={busy}>
            {busy ? 'Working…' : creating ? 'Create account' : 'Sign in'}
          </button>
        </form>
      </div>
    </main>
  )
}
