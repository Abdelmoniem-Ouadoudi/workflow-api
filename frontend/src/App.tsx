import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { SessionProvider } from './auth/SessionContext'
import { RequireAdmin, RequireAuth } from './auth/guards'
import { AdminUsers } from './components/AdminUsers'
import { AppShell } from './components/AppShell'
import { Board } from './components/Board'
import { Dashboard } from './components/Dashboard'
import { IssuePage } from './components/IssuePage'
import { JoinProject } from './components/JoinProject'
import { PendingApproval } from './components/PendingApproval'
import { ProjectList } from './components/ProjectList'
import { ProjectMembers } from './components/ProjectMembers'
import { SignIn } from './components/SignIn'

/**
 * The routes.
 *
 * <p>There was no router until M5, and its absence was a decision rather than an oversight —
 * recorded in docs/BACKLOG.md as item 35, which said it would land "at the next screen". This is
 * that milestone, and the argument finally settles itself: the whole point of a join code is that
 * a project manager can mail somebody a link, and a link is a URL. Screens switched by a `useState`
 * string cannot be linked to, bookmarked, or refreshed into.
 *
 * <p>Guards decide what is rendered, never what is allowed. Every rule below is enforced again in
 * work-service and auth-service, which is the only place it counts — a guard is one localStorage
 * edit away from being bypassed, and a service is not.
 *
 * <p>Every signed-in screen sits inside AppShell, so the sidebar is drawn once and stays put while
 * the page beside it changes.
 */
export function App() {
  return (
    <BrowserRouter>
      <SessionProvider>
        <Routes>
          {/* Open, because not having a session is the reason you are here. */}
          <Route path="/login" element={<SignIn mode="signIn" />} />
          <Route path="/register" element={<SignIn mode="createAccount" />} />
          <Route path="/pending" element={<PendingApproval />} />

          <Route element={<RequireAuth />}>
            <Route element={<AppShell />}>
              <Route path="/projects" element={<ProjectList />} />
              <Route path="/projects/:id/board" element={<Board />} />
              {/* A page of its own rather than a drawer over the board, so an issue has an
                  address: it can be linked in a comment, bookmarked, or refreshed into. */}
              <Route path="/projects/:id/issues/:issueId" element={<IssuePage />} />
              <Route path="/projects/:id/members" element={<ProjectMembers />} />
              <Route path="/dashboard" element={<Dashboard />} />

              {/* Both forms: a code pasted by hand, and a link somebody was sent. */}
              <Route path="/join" element={<JoinProject />} />
              <Route path="/join/:code" element={<JoinProject />} />

              <Route element={<RequireAdmin />}>
                <Route path="/admin/users" element={<AdminUsers />} />
              </Route>
            </Route>
          </Route>

          <Route path="/" element={<Navigate to="/projects" replace />} />
          {/* Anything else, rather than a blank page with no way out. */}
          <Route path="*" element={<Navigate to="/projects" replace />} />
        </Routes>
      </SessionProvider>
    </BrowserRouter>
  )
}
