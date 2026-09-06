import { Link, useNavigate } from 'react-router-dom'
import { useSession } from '../auth/SessionContext'

/**
 * Who is signed in, what they can do, and the way out.
 *
 * <p>It states the role as well as the name, because the role decides what the app will let you
 * do. Finding that out by being refused is worse than being told.
 *
 * <p>It reads the session from context rather than taking it as a prop: it now appears on screens
 * that are siblings under the router, with no shared parent to hand it down from.
 */
export function WhoAmI() {
  const { session, signOut, isAdmin } = useSession()
  const navigate = useNavigate()

  if (session === null) return null

  return (
    <div className="whoami">
      <span className="whoami__name">{session.username}</span>
      <span className="whoami__role">{session.role.toLowerCase()}</span>
      {isAdmin && (
        <Link className="whoami__out" to="/admin/users">
          People
        </Link>
      )}
      <button
        type="button"
        className="whoami__out"
        onClick={() => {
          signOut()
          navigate('/login', { replace: true })
        }}
      >
        Sign out
      </button>
    </div>
  )
}
