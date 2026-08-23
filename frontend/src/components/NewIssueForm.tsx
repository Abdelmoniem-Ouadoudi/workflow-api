import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import type { IssueType, Priority, User } from '../api/types'
import { ISSUE_TYPES, PRIORITIES } from '../api/types'

interface Props {
  projectId: number
  boardId: number
  users: User[]
  onCreated: () => void
  onFailed: (message: string) => void
}

export function NewIssueForm({ projectId, boardId, users, onCreated, onFailed }: Props) {
  const [title, setTitle] = useState('')
  const [type, setType] = useState<IssueType>('TASK')
  const [priority, setPriority] = useState<Priority>('MEDIUM')
  const [reporterId, setReporterId] = useState<number | ''>(users[0]?.id ?? '')
  const [titleError, setTitleError] = useState<string | undefined>()
  const [saving, setSaving] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (reporterId === '') {
      onFailed('Add a person before creating work. Every issue needs a reporter.')
      return
    }

    setSaving(true)
    setTitleError(undefined)
    try {
      await api.createIssue({ title, type, priority, projectId, boardId, reporterId })
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

      <select
        className="compose__select"
        value={reporterId}
        onChange={(event) => setReporterId(Number(event.target.value))}
        aria-label="Reported by"
      >
        {users.map((user) => (
          <option key={user.id} value={user.id}>
            {user.username}
          </option>
        ))}
      </select>

      <button className="button" type="submit" disabled={saving}>
        {saving ? 'Adding…' : 'Add issue'}
      </button>
    </form>
  )
}
