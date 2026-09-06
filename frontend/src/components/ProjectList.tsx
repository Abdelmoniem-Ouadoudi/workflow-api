import { useEffect, useState } from 'react'
import type { CSSProperties, FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import type { Project } from '../api/types'
import { useSession } from '../auth/SessionContext'
import { Notice } from './Notice'
import type { NoticeState } from './Notice'
import { WhoAmI } from './WhoAmI'

const dateFormatter = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' })

function formatShortDate(iso: string): string {
  return dateFormatter.format(new Date(iso))
}

/**
 * Your projects — since M5, only the ones you are actually on.
 *
 * The list is the visible half of the scoping. The half that matters is in work-service, which
 * checks membership on every issue, board and sprint: hiding a project here while its issues
 * stayed readable by id would be decoration, not access control.
 */
export function ProjectList() {
  const [projects, setProjects] = useState<Project[]>([])
  const [key, setKey] = useState('')
  const [name, setName] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [notice, setNotice] = useState<NoticeState | null>(null)
  const [loading, setLoading] = useState(true)

  const { canCreateProjects } = useSession()
  const navigate = useNavigate()

  async function load() {
    try {
      setProjects(await api.listProjects())
    } catch (error) {
      if (error instanceof ApiError) setNotice({ tone: 'stop', message: error.message })
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setErrors({})
    setNotice(null)
    try {
      const created = await api.createProject({ key, name })
      setKey('')
      setName('')
      setProjects((current) => [...current, created])
      // You are its project manager the moment it exists, so the members screen is the useful
      // next stop: it is where the join code is.
      navigate(`/projects/${created.id}/members`)
    } catch (error) {
      if (!(error instanceof ApiError)) return
      if (error.fieldErrors.length > 0) {
        setErrors(Object.fromEntries(error.fieldErrors.map((e) => [e.field, e.message])))
      } else {
        setNotice({ tone: 'stop', message: error.message })
      }
    }
  }

  return (
    <main className="page">
      <Notice notice={notice} onDismiss={() => setNotice(null)} />

      <header className="masthead">
        <div className="masthead__top">
          {projects.length > 0 ? (
            <p className="masthead__eyebrow">
              {projects.length} {projects.length === 1 ? 'project' : 'projects'} tracked
            </p>
          ) : (
            <span />
          )}
          <WhoAmI />
        </div>
        <h1 className="masthead__title">Workflow</h1>
        <p className="masthead__sub">
          Track work through three states. Nothing skips a step.{' '}
          <Link className="link" to="/dashboard">
            See the insights
          </Link>{' '}
          <Link className="link" to="/join">
            Join a project
          </Link>
        </p>
        <div className="masthead__rail" aria-hidden="true" />
      </header>

      {/*
        A DEVELOPER joins projects; they do not start them. Hiding the form is a courtesy - the
        rule is enforced in work-service, which answers 403 to the request whatever is rendered.
      */}
      {canCreateProjects && (
        <form className="compose compose--project" onSubmit={handleSubmit}>
          <div className="compose__field">
            <input
              className={`compose__input compose__input--key${errors.key ? ' compose__input--bad' : ''}`}
              placeholder="KEY"
              value={key}
              onChange={(event) => setKey(event.target.value.toUpperCase())}
              aria-label="Project key"
              aria-invalid={Boolean(errors.key)}
            />
            {errors.key && <span className="compose__error">{errors.key}</span>}
          </div>
          <div className="compose__field compose__field--grow">
            <input
              className={`compose__input${errors.name ? ' compose__input--bad' : ''}`}
              placeholder="Project name"
              value={name}
              onChange={(event) => setName(event.target.value)}
              aria-label="Project name"
              aria-invalid={Boolean(errors.name)}
            />
            {errors.name && <span className="compose__error">{errors.name}</span>}
          </div>
          <button className="button" type="submit">
            Create project
          </button>
        </form>
      )}

      {loading && <p className="page__loading">Loading projects…</p>}

      {!loading && projects.length === 0 && (
        <p className="page__empty">
          {canCreateProjects
            ? 'No projects yet. Create one above, or join one with a code somebody sent you.'
            : 'You are not on any project yet. Ask whoever runs one for its join code, then use “Join a project” above.'}
        </p>
      )}

      <ul className="projects">
        {projects.map((project, index) => (
          <li key={project.id}>
            <div className="projects__row" style={{ '--i': index } as CSSProperties}>
              <Link className="projects__open" to={`/projects/${project.id}/board`}>
                <span className="projects__key">{project.key}</span>
                <span className="projects__name">{project.name}</span>
                <span className="projects__date">{formatShortDate(project.createdAt)}</span>
                <span className="projects__go" aria-hidden="true">
                  →
                </span>
              </Link>
              <Link className="button button--quiet" to={`/projects/${project.id}/members`}>
                People
              </Link>
            </div>
          </li>
        ))}
      </ul>
    </main>
  )
}
