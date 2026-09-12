import { Fragment } from 'react'
import type { ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import type { Priority } from '../api/types'
import { useSession } from '../auth/SessionContext'

/**
 * The small pieces every screen shares: the top bar, an avatar, a priority pill.
 *
 * One file because each is a few lines and none has state of its own. Splitting them would give
 * five imports to read for what is really one vocabulary.
 */

/** Same person, same colour, on every screen. Derived from the name, so nothing is stored. */
const AVATAR_COLOURS = ['#6554c0', '#00875a', '#ff7452', '#0052cc', '#00a3bf', '#ff991f']

export function colourFor(text: string): string {
  let hash = 0
  for (const char of text) hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  return AVATAR_COLOURS[hash % AVATAR_COLOURS.length]
}

/** "hamza.ziouane" -> "HZ", "admin" -> "AD". */
export function initials(name: string): string {
  const parts = name.split(/[\s._-]+/).filter(Boolean)
  const letters = parts.length > 1 ? parts[0][0] + parts[1][0] : name.slice(0, 2)
  return letters.toUpperCase()
}

export function Avatar({ name, size }: { name: string | null; size?: 'md' }) {
  const sizeClass = size === 'md' ? ' avatar--md' : ''
  if (name === null) {
    return <span className={`avatar avatar--empty${sizeClass}`} title="Unassigned" />
  }
  return (
    <span className={`avatar${sizeClass}`} style={{ background: colourFor(name) }} title={name}>
      {initials(name)}
    </span>
  )
}

export function PriorityPill({ priority }: { priority: Priority }) {
  return <span className={`pill pill--${priority.toLowerCase()}`}>{priority.toLowerCase()}</span>
}

export interface Crumb {
  label: string
  /** Absent on the last crumb: it is where you already are. */
  to?: string
}

interface PageProps {
  crumbs: Crumb[]
  /** Buttons on the right of the top bar. The avatar and sign-out are always added after them. */
  actions?: ReactNode
  children: ReactNode
}

/**
 * The top bar and the content area under it.
 *
 * Every signed-in screen renders one. The breadcrumbs belong to the screen, not to the layout,
 * because only the screen knows the project name it just loaded.
 */
export function Page({ crumbs, actions, children }: PageProps) {
  const { session, signOut } = useSession()
  const navigate = useNavigate()

  return (
    <>
      <header className="topbar">
        <nav className="crumbs" aria-label="Breadcrumb">
          <Link to="/projects">Workflow</Link>
          {crumbs.map((crumb) => (
            <Fragment key={crumb.label}>
              <span className="crumbs__sep">›</span>
              {crumb.to ? (
                <Link to={crumb.to}>{crumb.label}</Link>
              ) : (
                <span className="crumbs__here">{crumb.label}</span>
              )}
            </Fragment>
          ))}
        </nav>

        <div className="topbar__actions">
          {actions}
          {session && <Avatar name={session.username} size="md" />}
          <button
            type="button"
            className="button button--ghost"
            onClick={() => {
              signOut()
              navigate('/login', { replace: true })
            }}
          >
            Log out
          </button>
        </div>
      </header>
      <div className="content">{children}</div>
    </>
  )
}

/** Title and subtitle at the top of a screen, with room for something on the right. */
export function PageHead({
  title,
  sub,
  aside,
}: {
  title: string
  sub?: ReactNode
  aside?: ReactNode
}) {
  return (
    <div className="page-head">
      <div>
        <h1 className="page-title">{title}</h1>
        {sub && <p className="page-sub">{sub}</p>}
      </div>
      {aside}
    </div>
  )
}
