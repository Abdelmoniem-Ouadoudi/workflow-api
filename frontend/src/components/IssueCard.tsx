import type { CSSProperties, KeyboardEvent } from 'react'
import type { IssueSummary, Priority } from '../api/types'

/** Matches the rank declared on the Priority enum in the backend. */
const PRIORITY_RANK: Record<Priority, number> = { LOW: 1, MEDIUM: 2, HIGH: 3, CRITICAL: 4 }

interface Props {
  index: number
  issue: IssueSummary
  lifted: boolean
  onLift: () => void
  onDrop: () => void
  onNudge: (direction: -1 | 1) => void
  onOpen: () => void
}

export function IssueCard({ index, issue, lifted, onLift, onDrop, onNudge, onOpen }: Props) {
  const rank = PRIORITY_RANK[issue.priority]

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
      className={`card${lifted ? ' card--lifted' : ''}`}
      style={{ '--i': index } as CSSProperties}
      draggable
      tabIndex={0}
      onDragStart={onLift}
      onDragEnd={onDrop}
      onClick={onOpen}
      onKeyDown={handleKeyDown}
      aria-label={`${issue.issueKey}, ${issue.title}, priority ${issue.priority.toLowerCase()}. Press Enter to open.`}
    >
      <div className="card__head">
        <span className="card__key">{issue.issueKey}</span>
        <span className="card__type">{issue.type}</span>
      </div>

      <h3 className="card__title">{issue.title}</h3>

      <div className="card__foot">
        <span
          className={`steps${issue.priority === 'CRITICAL' ? ' steps--critical' : ''}`}
          title={`Priority: ${issue.priority.toLowerCase()}`}
        >
          {[1, 2, 3, 4].map((step) => (
            <i key={step} className={step <= rank ? 'steps__on' : 'steps__off'} />
          ))}
        </span>
        <span className="card__who">{issue.assigneeUsername ?? 'Unassigned'}</span>
      </div>
    </article>
  )
}
