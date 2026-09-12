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
      <div className="gate__card">
        <div className="brand brand--dark">
          <span className="brand__mark">W</span>
          Workflow
        </div>

        <h1 className="gate__title">{creating ? 'Create your account' : 'Welcome back'}</h1>
        <p className="gate__sub">
          {creating
            ? 'An administrator approves it before you can sign in.'
            : 'Sign in to reach your projects.'}
        </p>

        <form className="gate__form" onSubmit={handleSubmit}>
          <Notice notice={notice} onDismiss={() => setNotice(null)} />

          <div className="field">
            <label className="field__label" htmlFor="username">
              Username
            </label>
            <input
              id="username"
              className={`input${errors.username ? ' input--bad' : ''}`}
              placeholder="Your username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              autoComplete="username"
              aria-invalid={Boolean(errors.username)}
            />
            {errors.username && <span className="field__error">{errors.username}</span>}
          </div>

          {creating && (
            <div className="field">
              <label className="field__label" htmlFor="email">
                Email
              </label>
              <input
                id="email"
                type="email"
                className={`input${errors.email ? ' input--bad' : ''}`}
                placeholder="you@company.com"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                autoComplete="email"
                aria-invalid={Boolean(errors.email)}
              />
              {errors.email && <span className="field__error">{errors.email}</span>}
            </div>
          )}

          <div className="field">
            <label className="field__label" htmlFor="password">
              Password
            </label>
            <input
              id="password"
              type="password"
              className={`input${errors.password ? ' input--bad' : ''}`}
              placeholder="••••••••"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete={creating ? 'new-password' : 'current-password'}
              aria-invalid={Boolean(errors.password)}
            />
            {errors.password && <span className="field__error">{errors.password}</span>}
            {creating && !errors.password && (
              <span className="field__hint">At least 8 characters.</span>
            )}
          </div>

          {/*
            There was a role dropdown here until M5, on a page anybody can open, which meant
            anyone could make themselves an administrator. Everybody now registers the same way
            and an administrator decides what they are.
          */}
          <button className="button button--lg" type="submit" disabled={busy}>
            {busy ? 'Working…' : creating ? 'Create account' : 'Sign in'}
          </button>

          <div className="gate__or">or</div>

          <Link className="button button--ghost button--lg" to={creating ? '/login' : '/register'}>
            {creating ? 'Sign in instead' : 'Create account'}
          </Link>
        </form>

        <p className="gate__foot">
          New accounts wait for an administrator to approve them before they can sign in.
        </p>
      </div>
    </main>
  )
}
