import { NavLink, Outlet, useMatch } from 'react-router-dom'
import { useCurrentSession, useSession } from '../auth/SessionContext'
import { Avatar } from './ui'

function navClass({ isActive }: { isActive: boolean }): string {
  return `nav__link${isActive ? ' nav__link--on' : ''}`
}

/**
 * The frame around every signed-in screen: the blue sidebar on the left, the page on the right.
 *
 * "Board" and "People" appear only while a project is open, because they mean that project's
 * board and that project's people. Offering them on the project list would be a link to nowhere.
 * The project id is read from the URL, which is the one place it already is.
 */
export function AppShell() {
  const session = useCurrentSession()
  const { isAdmin } = useSession()
  const inProject = useMatch('/projects/:id/*')
  const projectId = inProject?.params.id
  // An issue page is reached from the board and goes back to it, so it counts as being on it.
  const onIssue = useMatch('/projects/:id/issues/:issueId') !== null

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand__mark">W</span>
          Workflow
        </div>

        <nav className="nav" aria-label="Main">
          <NavLink className={navClass} to="/projects" end>
            Projects
          </NavLink>
          {projectId && (
            <>
              <NavLink
                className={({ isActive }) => navClass({ isActive: isActive || onIssue })}
                to={`/projects/${projectId}/board`}
              >
                Board
              </NavLink>
              <NavLink className={navClass} to={`/projects/${projectId}/members`}>
                People
              </NavLink>
            </>
          )}
          <NavLink className={navClass} to="/dashboard">
            Insights
          </NavLink>
          <NavLink className={navClass} to="/join">
            Join a project
          </NavLink>

          {isAdmin && (
            <>
              <p className="nav__label">Administration</p>
              <NavLink className={navClass} to="/admin/users">
                Accounts
              </NavLink>
            </>
          )}
        </nav>

        {/* The role is stated next to the name because it decides what the app lets you do.
            Finding that out by being refused is worse than being told. */}
        <div className="me">
          <Avatar name={session.username} size="md" />
          <div>
            <p className="me__name">{session.username}</p>
            <p className="me__role">{session.role.toLowerCase()}</p>
          </div>
        </div>
      </aside>

      <main className="main">
        <Outlet />
      </main>
    </div>
  )
}
