import { useEffect, useState } from 'react'
import { api, setSessionExpiredHandler } from './api/client'
import type { Project } from './api/types'
import { endSession, getSession } from './auth/session'
import type { Session } from './auth/session'
import { Board } from './components/Board'
import { ProjectList } from './components/ProjectList'
import { SignIn } from './components/SignIn'

type Check = 'checking' | 'done'

/**
 * Three screens now: sign in, then the project list or a board. Still switched by state and still
 * no router — recorded in docs/BACKLOG.md.
 */
export function App() {
  const [session, setSession] = useState<Session | null>(getSession)
  const [project, setProject] = useState<Project | null>(null)
  const [check, setCheck] = useState<Check>(session === null ? 'done' : 'checking')

  // The API clears the session on any 401 — an expired token, or a restarted auth-service with a
  // different secret. This is how the app finds out and returns to the sign-in screen.
  useEffect(() => {
    setSessionExpiredHandler(() => {
      setSession(null)
      setProject(null)
    })
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

  function signOut() {
    endSession()
    setSession(null)
    setProject(null)
  }

  if (session === null) {
    return (
      <SignIn
        onSignedIn={(started) => {
          setSession(started)
          setCheck('done')
        }}
      />
    )
  }

  if (check === 'checking') {
    return <p className="page__loading page">Checking your session…</p>
  }

  return project === null ? (
    <ProjectList session={session} onOpen={setProject} onSignOut={signOut} />
  ) : (
    <Board project={project} session={session} onLeave={() => setProject(null)} />
  )
}
