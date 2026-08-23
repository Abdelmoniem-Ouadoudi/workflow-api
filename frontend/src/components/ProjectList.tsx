import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import type { Project } from '../api/types'
import { Notice } from './Notice'
import type { NoticeState } from './Notice'

interface Props {
  onOpen: (project: Project) => void
}

export function ProjectList({ onOpen }: Props) {
  const [projects, setProjects] = useState<Project[]>([])
  const [key, setKey] = useState('')
  const [name, setName] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [notice, setNotice] = useState<NoticeState | null>(null)
  const [loading, setLoading] = useState(true)

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
        <h1 className="masthead__title">Workflow</h1>
        <p className="masthead__sub">Track work through three states. Nothing skips a step.</p>
      </header>

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

      {loading && <p className="page__loading">Loading projects…</p>}

      {!loading && projects.length === 0 && (
        <p className="page__empty">No projects yet. Create one above to start tracking work.</p>
      )}

      <ul className="projects">
        {projects.map((project) => (
          <li key={project.id}>
            <button type="button" className="projects__row" onClick={() => onOpen(project)}>
              <span className="projects__key">{project.key}</span>
              <span className="projects__name">{project.name}</span>
              <span className="projects__go" aria-hidden="true">
                →
              </span>
            </button>
          </li>
        ))}
      </ul>
    </main>
  )
}
