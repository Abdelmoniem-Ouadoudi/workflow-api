import { useEffect, useState } from 'react'
import { api, setSessionExpiredHandler } from './api/client'
import type { Project } from './api/types'
import { endSession, getSession } from './auth/session'
import type { Session } from './auth/session'
import { Board } from './components/Board'
import { Dashboard } from './components/Dashboard'
import { ProjectList } from './components/ProjectList'
import { SignIn } from './components/SignIn'

type Check = 'checking' | 'done'

/** Which screen is showing. Four of them now, and still no router — docs/BACKLOG.md item 11. */
type Screen = 'projects' | 'board' | 'dashboard'

/**
 * Four screens: sign in, then the project list, a board, or the dashboard.
 *
 * Still switched by state. The router argument gets stronger with each screen added — you cannot
 * refresh into the dashboard or link somebody to it — but a router is a dependency and a concept,
 * and the answer to "why is there no router" should be a decision rather than an oversight.
 */
export function App() {
  const [session, setSession] = useState<Session | null>(getSession)
  const [project, setProject] = useState<Project | null>(null)
  const [screen, setScreen] = useState<Screen>('projects')
  const [check, setCheck] = useState<Check>(session === null ? 'done' : 'checking')

  // The API clears the session on any 401 — an expired token, or a restarted auth-service with a
  // different secret. This is how the app finds out and returns to the sign-in screen.
  useEffect(() => {
    setSessionExpiredHandler(() => {
      setSession(null)
      setProject(null)
      setScreen('projects')
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
    setScreen('projects')
  }

  function openProject(opened: Project) {
    setProject(opened)
    setScreen('board')
  }

  function backToProjects() {
    setProject(null)
    setScreen('projects')
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

  if (screen === 'dashboard') {
    return <Dashboard session={session} onLeave={backToProjects} onSignOut={signOut} />
  }

  if (screen === 'board' && project !== null) {
    return <Board project={project} session={session} onLeave={backToProjects} />
  }

  return (
    <ProjectList
      session={session}
      onOpen={openProject}
      onSignOut={signOut}
      onOpenDashboard={() => setScreen('dashboard')}
    />
  )
}
