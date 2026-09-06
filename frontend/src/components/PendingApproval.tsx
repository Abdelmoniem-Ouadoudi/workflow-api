import { Link, useLocation } from 'react-router-dom'

interface LocationState {
  username?: string
}

/**
 * Where you land after registering, and after trying to sign in too early.
 *
 * Both arrive here on purpose. The two events feel identical from the outside — "I made an
 * account and I cannot get in" — so answering them with one page, rather than a success message
 * in one place and a red error in the other, is the honest shape. Nothing here is an error.
 */
export function PendingApproval() {
  const username = (useLocation().state as LocationState | null)?.username

  return (
    <main className="gate">
      <div className="gate__panel">
        <header className="gate__head">
          <p className="masthead__eyebrow">Waiting</p>
          <h1 className="gate__title">Almost there</h1>
          <p className="gate__sub">
            {username === undefined
              ? 'Your account is waiting for an administrator to approve it.'
              : `${username} is waiting for an administrator to approve it.`}
          </p>
          <div className="masthead__rail" aria-hidden="true" />
        </header>

        <p className="gate__hint">
          An administrator decides what you can do — whether you can start projects of your own, or
          join ones other people run. Until then signing in will send you back here.
        </p>

        <p className="gate__hint">
          Once you are in, a project manager can send you a join code for their project.
        </p>

        <Link className="button gate__submit" to="/login">
          Back to sign in
        </Link>
      </div>
    </main>
  )
}
