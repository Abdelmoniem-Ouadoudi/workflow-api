import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useSession } from './SessionContext'

/**
 * Route guards.
 *
 * <p>They decide what is <em>rendered</em>, never what is allowed. Every rule here is enforced
 * again in work-service and auth-service, which is the only place it counts — a guard is one
 * `localStorage` edit away from being bypassed, and the server is not. What these buy is that
 * somebody who cannot do a thing is not shown a screen that will only fail.
 */

/** Signed in, or back to the sign-in screen. */
export function RequireAuth() {
  const { session, checking } = useSession()
  const location = useLocation()

  if (checking) {
    return <p className="page__loading page">Checking your session…</p>
  }
  if (session === null) {
    // Remember where they were going, so signing in lands them there rather than on the project
    // list. This is the whole reason a mailed /join/CODE link is worth having: it survives being
    // bounced through the login screen.
    return <Navigate to="/login" replace state={{ from: location }} />
  }
  return <Outlet />
}

/**
 * ADMIN only.
 *
 * <p>Sends a non-admin to the project list rather than to the sign-in screen: they are signed in
 * perfectly well, they simply have no business here, and asking them to log in again would
 * suggest the wrong fix.
 */
export function RequireAdmin() {
  const { isAdmin } = useSession()
  return isAdmin ? <Outlet /> : <Navigate to="/projects" replace />
}

/** Only somebody who could actually create a project sees the form. */
export function RequireProjectCreator() {
  const { canCreateProjects } = useSession()
  return canCreateProjects ? <Outlet /> : <Navigate to="/projects" replace />
}
