import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import type { ProjectLookup } from '../api/types'
import { Notice } from './Notice'
import type { NoticeState } from './Notice'

type Step = 'entering' | 'confirming' | 'asked'

/**
 * Joining a project with a code somebody sent you.
 *
 * Two steps, and the middle one is the point: the code is checked, the project's name comes back,
 * and only then is a request sent. Somebody who was forwarded the wrong code finds out here
 * rather than by appearing in a stranger's project.
 *
 * The code never admits anybody by itself — a project manager still says yes. A code sent by mail
 * gets forwarded and quoted in replies, so treating possession of one as permission would make a
 * project as private as its most careless member's inbox.
 */
export function JoinProject() {
  // /join/:code is the mailed link. /join with no code shows an empty box to paste into.
  const { code } = useParams<{ code: string }>()

  const [joinCode, setJoinCode] = useState(code ?? '')
  const [project, setProject] = useState<ProjectLookup | null>(null)
  const [step, setStep] = useState<Step>('entering')
  const [notice, setNotice] = useState<NoticeState | null>(null)
  const [busy, setBusy] = useState(false)

  const navigate = useNavigate()

  const lookUp = useCallback(async (value: string) => {
    const trimmed = value.trim().toUpperCase()
    if (trimmed === '') return

    setNotice(null)
    setBusy(true)
    try {
      setProject(await api.lookupProject(trimmed))
      setStep('confirming')
    } catch (error) {
      if (error instanceof ApiError) {
        setNotice({
          tone: 'stop',
          message:
            error.status === 404
              ? 'No project uses that code. Check it with whoever sent it to you.'
              : error.message,
        })
      }
    } finally {
      setBusy(false)
    }
  }, [])

  // A mailed link should not make somebody paste the code they just clicked.
  useEffect(() => {
    if (code !== undefined) void lookUp(code)
  }, [code, lookUp])

  async function ask() {
    if (project === null) return

    setNotice(null)
    setBusy(true)
    try {
      await api.requestToJoin(project.id, joinCode.trim().toUpperCase())
      setStep('asked')
    } catch (error) {
      if (error instanceof ApiError) {
        // Already a member is a happy ending, not a failure: they went looking for a project they
        // are already on, so take them to it.
        if (error.code === 'ALREADY_A_MEMBER') {
          navigate('/projects')
          return
        }
        setNotice({ tone: error.code === 'REQUEST_ALREADY_PENDING' ? 'note' : 'stop', message: error.message })
        if (error.code === 'REQUEST_ALREADY_PENDING') setStep('asked')
      }
    } finally {
      setBusy(false)
    }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    void lookUp(joinCode)
  }

  return (
    <main className="page">
      <header className="masthead">
        <div>
          <p className="masthead__eyebrow">Join</p>
          <h1 className="masthead__title">Join a project</h1>
        </div>
        <Link className="button button--quiet" to="/projects">
          My projects
        </Link>
      </header>
      <div className="masthead__rail" aria-hidden="true" />

      <Notice notice={notice} onDismiss={() => setNotice(null)} />

      {step === 'asked' && project !== null ? (
        <section className="gate__panel">
          <h2 className="gate__title">Request sent</h2>
          <p className="gate__sub">
            The project manager of {project.name} has been asked to add you. The project appears in
            your list once they accept.
          </p>
          <Link className="button gate__submit" to="/projects">
            My projects
          </Link>
        </section>
      ) : step === 'confirming' && project !== null ? (
        <section className="gate__panel">
          <h2 className="gate__title">
            {project.name} <span className="chip">{project.key}</span>
          </h2>
          <p className="gate__sub">Is this the project you were told about?</p>
          <div className="chip__actions">
            <button className="button" type="button" onClick={() => void ask()} disabled={busy}>
              {busy ? 'Asking…' : 'Ask to join'}
            </button>
            <button
              className="button button--quiet"
              type="button"
              onClick={() => {
                setProject(null)
                setStep('entering')
              }}
            >
              Use another code
            </button>
          </div>
        </section>
      ) : (
        <form className="gate__form gate__panel" onSubmit={handleSubmit}>
          <div className="compose__field">
            <label className="gate__label" htmlFor="joinCode">
              Join code
            </label>
            <input
              id="joinCode"
              className="compose__input"
              value={joinCode}
              // Upper-cased as it is typed, because that is how the code is generated and how it
              // was written in the message. Nobody should have to hold shift for twelve characters.
              onChange={(event) => setJoinCode(event.target.value.toUpperCase())}
              placeholder="ABCD2345EFGH"
              maxLength={16}
              autoComplete="off"
            />
            <span className="gate__hint">
              Twelve characters, from whoever runs the project. It is not the project key you see on
              tickets.
            </span>
          </div>

          <button className="button gate__submit" type="submit" disabled={busy}>
            {busy ? 'Checking…' : 'Find the project'}
          </button>
        </form>
      )}
    </main>
  )
}
