import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import type { IssueType, Priority } from '../api/types'
import { ISSUE_TYPES, PRIORITIES } from '../api/types'
import { DuplicatePanel } from './DuplicatePanel'

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
    <form className="compose" onSubmit={handleSubmit}>
      <div className="compose__field compose__field--grow">
        <input
          className={`compose__input${titleError ? ' compose__input--bad' : ''}`}
          placeholder="What needs doing?"
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          aria-label="Issue title"
          aria-invalid={Boolean(titleError)}
        />
        {titleError && <span className="compose__error">{titleError}</span>}
      </div>

      <select
        className="compose__select"
        value={type}
        onChange={(event) => setType(event.target.value as IssueType)}
        aria-label="Type"
      >
        {ISSUE_TYPES.map((option) => (
          <option key={option} value={option}>
            {option.toLowerCase()}
          </option>
        ))}
      </select>

      <select
        className="compose__select"
        value={priority}
        onChange={(event) => setPriority(event.target.value as Priority)}
        aria-label="Priority"
      >
        {PRIORITIES.map((option) => (
          <option key={option} value={option}>
            {option.toLowerCase()}
          </option>
        ))}
      </select>

      <button className="button" type="submit" disabled={saving}>
        {saving ? 'Adding…' : 'Add issue'}
      </button>

      {/* Full width under the row, so a match is read before the button is pressed rather than
          squeezed beside it. Renders nothing at all when there is no match. */}
      <DuplicatePanel text={title} projectKey={projectKey} onOpen={onOpenExisting} />
    </form>
  )
}
