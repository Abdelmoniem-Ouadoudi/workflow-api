import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import type { Dashboard, Project } from '../api/types'
import { useSession } from '../auth/SessionContext'
import { Notice } from './Notice'
import type { NoticeState } from './Notice'
import { Page, PageHead, colourFor } from './ui'

const dateFormatter = new Intl.DateTimeFormat(undefined, {
  month: 'short',
  day: 'numeric',
  year: 'numeric',
})

/**
 * Your projects — since M5, only the ones you are actually on.
 *
 * The list is the visible half of the scoping. The half that matters is in work-service, which
 * checks membership on every issue, board and sprint: hiding a project here while its issues
 * stayed readable by id would be decoration, not access control.
 */
export function ProjectList() {
  const [projects, setProjects] = useState<Project[]>([])
  const [numbers, setNumbers] = useState<Dashboard | null>(null)
  const [key, setKey] = useState('')
  const [name, setName] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [notice, setNotice] = useState<NoticeState | null>(null)
  const [loading, setLoading] = useState(true)

  const { canCreateProjects } = useSession()
  const navigate = useNavigate()

  useEffect(() => {
    let cancelled = false
    api
      .listProjects()
      .then((list) => {
        if (!cancelled) setProjects(list)
      })
      .catch((error) => {
        if (!cancelled && error instanceof ApiError) setNotice({ tone: 'stop', message: error.message })
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    // The four figures across the top are a courtesy. If they cannot be read the projects still
    // can, so a failure here draws no tiles rather than an error over the list.
    api
      .dashboard()
      .then((loaded) => {
        if (!cancelled) setNumbers(loaded)
      })
      .catch(() => undefined)

    return () => {
      cancelled = true
    }
  }, [])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setErrors({})
    setNotice(null)
    try {
      const created = await api.createProject({ key, name })
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

  const openIssues = numbers?.byStatus
    .filter((row) => row.label !== 'DONE')
    .reduce((sum, row) => sum + row.count, 0)

  return (
    <Page
      crumbs={[{ label: 'Projects' }]}
      actions={
        <Link className="button" to="/join">
          Join a project
        </Link>
      }
    >
      <PageHead title="Projects" sub="All projects you're a member of" />
      <Notice notice={notice} onDismiss={() => setNotice(null)} />

      {numbers && (
        <section className="stats">
          <Stat value={String(projects.length)} label="Projects you're on" />
          <Stat value={String(openIssues)} label="Open issues" />
          <Stat value={String(numbers.awaitingReview)} label="Awaiting AI review" />
          {/* Null and zero are different claims: zero says the AI is always wrong, null says
              nobody has judged a suggestion yet. */}
          <Stat
            value={numbers.aiAgreementRate === null ? '—' : `${numbers.aiAgreementRate}%`}
            label="AI agreement rate"
          />
        </section>
      )}

      {/*
        A DEVELOPER joins projects; they do not start them. Hiding the form is a courtesy - the
        rule is enforced in work-service, which answers 403 to the request whatever is rendered.
      */}
      {canCreateProjects && (
        <form className="card new-project" onSubmit={handleSubmit}>
          <div className="field field--key">
            <label className="field__label" htmlFor="project-key">
              Key
            </label>
            <input
              id="project-key"
              className={`input${errors.key ? ' input--bad' : ''}`}
              placeholder="WM"
              value={key}
              onChange={(event) => setKey(event.target.value.toUpperCase())}
              aria-invalid={Boolean(errors.key)}
            />
            {errors.key && <span className="field__error">{errors.key}</span>}
          </div>
          <div className="field field--grow">
            <label className="field__label" htmlFor="project-name">
              New project
            </label>
            <input
              id="project-name"
              className={`input${errors.name ? ' input--bad' : ''}`}
              placeholder="Project name"
              value={name}
              onChange={(event) => setName(event.target.value)}
              aria-invalid={Boolean(errors.name)}
            />
            {errors.name && <span className="field__error">{errors.name}</span>}
          </div>
          <button className="button" type="submit">
            Create project
          </button>
        </form>
      )}

      {loading && <p className="loading">Loading projects…</p>}

      {!loading && projects.length === 0 && (
        <p className="empty">
          {canCreateProjects
            ? 'No projects yet. Create one above, or join one with a code somebody sent you.'
            : 'You are not on any project yet. Ask whoever runs one for its join code, then use “Join a project”.'}
        </p>
      )}

      <div className="project-grid">
        {projects.map((project) => (
          <article className="project" key={project.id}>
            <div className="project__top">
              <span className="project__tile" style={{ background: colourFor(project.key) }}>
                {project.key.slice(0, 2)}
              </span>
              <div>
                <h2 className="project__name">{project.name}</h2>
                <p className="project__meta">
                  {project.key} · Created {dateFormatter.format(new Date(project.createdAt))}
                </p>
              </div>
            </div>

            <p className="project__desc">{project.description ?? 'No description.'}</p>

            <div className="project__actions">
              <Link className="button button--soft button--sm" to={`/projects/${project.id}/board`}>
                Open board
              </Link>
              <Link className="button button--ghost button--sm" to={`/projects/${project.id}/members`}>
                People
              </Link>
            </div>
          </article>
        ))}
      </div>
    </Page>
  )
}

function Stat({ value, label }: { value: string; label: string }) {
  return (
    <div className="stat">
      <p className="stat__value">{value}</p>
      <p className="stat__label">{label}</p>
    </div>
  )
}
