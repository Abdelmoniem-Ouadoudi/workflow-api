import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { api, setSessionExpiredHandler } from '../api/client'
import { endSession, getSession } from './session'
import type { Session } from './session'

/**
 * The signed-in user, available anywhere without threading a prop through every screen.
 *
 * This is the project's first context, and it arrived with the router rather than before it.
 * While there were four screens and one component tree, passing `session` down as a prop was
 * honest and visible. Routes break that: a guard around `/admin/users` and a header inside
 * `/projects/:id/board` are siblings with no parent that could hand either of them anything.
 *
 * It holds one value that changes rarely, so there is nothing to memoise beyond the object itself
 * and no reason to reach for a state library.
 */

type Check = 'checking' | 'done'

interface SessionState {
  session: Session | null
  /** True while a stored token is being confirmed with the server on boot. */
  checking: boolean
  signIn: (session: Session) => void
  signOut: () => void
  isAdmin: boolean
  /** Only a MANAGER or an ADMIN may start a project. A DEVELOPER joins them. */
  canCreateProjects: boolean
}

const SessionContext = createContext<SessionState | null>(null)

export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(getSession)
  const [check, setCheck] = useState<Check>(session === null ? 'done' : 'checking')

  // The API clears the session on any 401 — an expired token, or a restarted auth-service with a
  // different secret. This is how the app finds out and falls back to the sign-in screen.
  useEffect(() => {
    setSessionExpiredHandler(() => setSession(null))
  }, [])

  // A token from localStorage is only unexpired, which is not the same as valid. Asking the
  // server once on boot means the app never renders a board it is about to fail to load.
  useEffect(() => {
    if (session === null || check === 'done') return

    api
      .me()
      .then(() => setCheck('done'))
      .catch(() => {
        // A 401 already cleared the session through the handler above. Anything else means the
        // server is unreachable, and there is nothing to show behind a sign-in screen anyway.
        setSession(null)
        setCheck('done')
      })
  }, [session, check])

  const signIn = useCallback((started: Session) => {
    setSession(started)
    setCheck('done')
  }, [])

  const signOut = useCallback(() => {
    endSession()
    setSession(null)
  }, [])

  const value = useMemo<SessionState>(
    () => ({
      session,
      checking: check === 'checking',
      signIn,
      signOut,
      isAdmin: session?.role === 'ADMIN',
      canCreateProjects: session?.role === 'MANAGER' || session?.role === 'ADMIN',
    }),
    [session, check, signIn, signOut],
  )

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}

export function useSession(): SessionState {
  const value = useContext(SessionContext)
  if (value === null) {
    // A loud failure rather than a null session, which would silently render the signed-out
    // version of a screen that is only reachable when signed in.
    throw new Error('useSession must be used inside a SessionProvider')
  }
  return value
}

/**
 * The signed-in user, for a screen that cannot render without one.
 *
 * Everything under RequireAuth is in that position, and having each of them re-check for null
 * would be noise around a case the router has already ruled out.
 */
export function useCurrentSession(): Session {
  const { session } = useSession()
  if (session === null) {
    throw new Error('This screen requires a signed-in user')
  }
  return session
}
