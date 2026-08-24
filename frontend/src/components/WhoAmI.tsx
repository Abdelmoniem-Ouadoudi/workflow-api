import type { Session } from '../auth/session'

interface Props {
  session: Session
  onSignOut: () => void
}

/**
 * Who is signed in, and the way out.
 *
 * <p>It states the role as well as the name, because the role decides what the app will let you
 * do. Finding that out by being refused is worse than being told.
 */
export function WhoAmI({ session, onSignOut }: Props) {
  return (
    <div className="whoami">
      <span className="whoami__name">{session.username}</span>
      <span className="whoami__role">{session.role.toLowerCase()}</span>
      <button type="button" className="whoami__out" onClick={onSignOut}>
        Sign out
      </button>
    </div>
  )
}
