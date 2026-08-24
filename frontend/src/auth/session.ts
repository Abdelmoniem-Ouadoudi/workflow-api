import type { CurrentUser, Role, TokenResponse } from '../api/types'

/**
 * The signed-in session, held in localStorage so a page refresh does not sign you out.
 *
 * The token is the only thing worth storing: it already carries the user id, the username and the
 * role, signed. Keeping a separate copy of the user would be a second source of truth that can go
 * stale, so the whole TokenResponse is stored as one record and read back as one.
 *
 * localStorage is readable by any script on this origin, so a cross-site script could take the
 * token. The alternative — an HttpOnly cookie — cannot be read by JavaScript, but it is attached
 * to every request automatically, which is exactly what makes CSRF possible and would mean
 * bringing back the CSRF defences the API deliberately does without. Tokens are short-lived for
 * this reason. Recorded in docs/BACKLOG.md.
 */

const STORAGE_KEY = 'workflow.session'

export interface Session {
  token: string
  expiresAt: string
  userId: number
  username: string
  role: Role
}

let current: Session | null = read()

function read(): Session | null {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (raw === null) return null

  try {
    const session = JSON.parse(raw) as Session
    // A token past its expiry is refused by the server anyway. Dropping it here means the app
    // opens on the login screen instead of flashing the board and then failing every request.
    if (new Date(session.expiresAt).getTime() <= Date.now()) {
      localStorage.removeItem(STORAGE_KEY)
      return null
    }
    return session
  } catch {
    // Someone edited it by hand, or an older version wrote a different shape.
    localStorage.removeItem(STORAGE_KEY)
    return null
  }
}

export function getSession(): Session | null {
  return current
}

export function startSession(response: TokenResponse): Session {
  current = response
  localStorage.setItem(STORAGE_KEY, JSON.stringify(response))
  return current
}

export function endSession(): void {
  current = null
  localStorage.removeItem(STORAGE_KEY)
}

export function toCurrentUser(session: Session): CurrentUser {
  return { userId: session.userId, username: session.username, role: session.role }
}
