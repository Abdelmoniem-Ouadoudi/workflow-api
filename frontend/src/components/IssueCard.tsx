import type { KeyboardEvent } from 'react'
import type { IssueSummary } from '../api/types'
import { Avatar, PriorityPill } from './ui'

interface Props {
  issue: IssueSummary
  lifted: boolean
  onLift: () => void
  onDrop: () => void
  onNudge: (direction: -1 | 1) => void
  onOpen: () => void
}

export function IssueCard({ issue, lifted, onLift, onDrop, onNudge, onOpen }: Props) {
  function handleKeyDown(event: KeyboardEvent<HTMLElement>) {
    // Native drag and drop is mouse only, so arrow keys move the card too.
    if (event.key === 'ArrowRight') {
      event.preventDefault()
      onNudge(1)
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault()
      onNudge(-1)
    } else if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      onOpen()
    }
  }

  return (
    <article
      className={`issue${lifted ? ' issue--lifted' : ''}`}
      draggable
      tabIndex={0}
      onDragStart={onLift}
      onDragEnd={onDrop}
      onClick={onOpen}
      onKeyDown={handleKeyDown}
      aria-label={`${issue.issueKey}, ${issue.title}, priority ${issue.priority.toLowerCase()}. Press Enter to open.`}
    >
      <div className="issue__top">
        <span>{issue.issueKey}</span>
        <span className="issue__type">{issue.type}</span>
      </div>

      <h3 className="issue__title">{issue.title}</h3>

      <div className="issue__foot">
        <PriorityPill priority={issue.priority} />
        <Avatar name={issue.assigneeUsername} />
      </div>
    </article>
  )
}
