import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import type { IssueType, Priority } from '../api/types'
import { ISSUE_TYPES, PRIORITIES } from '../api/types'
import { DuplicatePanel } from './DuplicatePanel'

/** The top bar's "Create issue" button focuses this input rather than opening a second form. */
export const NEW_ISSUE_TITLE_ID = 'new-issue-title'

interface Props {
  projectId: number
  /** Scopes the duplicate check. A duplicate in another project is not a duplicate. */
  projectKey: string
  boardId: number
  onCreated: () => void
  onFailed: (message: string) => void
  /** Opens an existing ticket, when the duplicate panel finds the one being retyped. */
  onOpenExisting: (issueId: number) => void
}

/**
 * No "reported by" field any more. The server takes the reporter from the token, so the choice
 * was never real — it was a way to file work under someone else's name. One fewer control, and
 * one fewer thing that could be wrong.
 */
export function NewIssueForm({
  projectId,
  projectKey,
  boardId,
  onCreated,
  onFailed,
  onOpenExisting,
}: Props) {
  const [title, setTitle] = useState('')
  const [type, setType] = useState<IssueType>('TASK')
  const [priority, setPriority] = useState<Priority>('MEDIUM')
  const [titleError, setTitleError] = useState<string | undefined>()
  const [saving, setSaving] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setTitleError(undefined)
    try {
      await api.createIssue({ title, type, priority, projectId, boardId })
      setTitle('')
      onCreated()
    } catch (error) {
      if (error instanceof ApiError) {
        // The @NotBlank and @Size messages come from the DTO, so the rule is written once.
        setTitleError(error.forField('title'))
        if (!error.forField('title')) onFailed(error.message)
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="card compose" onSubmit={handleSubmit}>
      <h2 className="card__title">New issue</h2>

      <div className="field">
        <label className="field__label" htmlFor={NEW_ISSUE_TITLE_ID}>
          Title
        </label>
        <input
          id={NEW_ISSUE_TITLE_ID}
          className={`input${titleError ? ' input--bad' : ''}`}
          placeholder="What needs doing?"
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          aria-invalid={Boolean(titleError)}
        />
        {titleError && <span className="field__error">{titleError}</span>}
      </div>

      <div className="compose__row">
        <div className="field">
          <label className="field__label" htmlFor="new-issue-type">
            Type
          </label>
          <select
            id="new-issue-type"
            className="select"
            value={type}
            onChange={(event) => setType(event.target.value as IssueType)}
          >
            {ISSUE_TYPES.map((option) => (
              <option key={option} value={option}>
                {option.charAt(0) + option.slice(1).toLowerCase()}
              </option>
            ))}
          </select>
        </div>

        <div className="field">
          <label className="field__label" htmlFor="new-issue-priority">
            Priority
          </label>
          <select
            id="new-issue-priority"
            className="select"
            value={priority}
            onChange={(event) => setPriority(event.target.value as Priority)}
          >
            {PRIORITIES.map((option) => (
              <option key={option} value={option}>
                {option.charAt(0) + option.slice(1).toLowerCase()}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Above the button, so a match is read before it is pressed. Renders nothing at all when
          there is no match. */}
      <DuplicatePanel text={title} projectKey={projectKey} onOpen={onOpenExisting} />

      <button className="button button--lg" type="submit" disabled={saving}>
        {saving ? 'Adding…' : 'Add issue'}
      </button>
    </form>
  )
}
