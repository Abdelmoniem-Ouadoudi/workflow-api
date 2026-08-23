import type { CSSProperties } from 'react'
import type { IssueSummary, Status } from '../api/types'
import { STATUS_LABEL } from '../api/types'
import { IssueCard } from './IssueCard'

/**
 * idle   — nothing is being dragged
 * source — the card came from here
 * clear  — the workflow permits the move, this column will take it
 * held   — the workflow refuses the move
 */
export type ColumnState = 'idle' | 'source' | 'clear' | 'held'

const EMPTY_TEXT: Record<Status, string> = {
  TO_DO: 'No work waiting.',
  IN_PROGRESS: 'Nothing started.',
  DONE: 'Nothing finished yet.',
}

interface Props {
  index: number
  status: Status
  issues: IssueSummary[]
  state: ColumnState
  holdReason: string | null
  liftedId: number | null
  onLift: (issue: IssueSummary) => void
  onLiftEnd: () => void
  onDropHere: () => void
  onNudge: (issue: IssueSummary, direction: -1 | 1) => void
  onOpen: (issue: IssueSummary) => void
}

export function Column({
  index,
  status,
  issues,
  state,
  holdReason,
  liftedId,
  onLift,
  onLiftEnd,
  onDropHere,
  onNudge,
  onOpen,
}: Props) {
  return (
    <section
      className={`column column--${state}`}
      style={{ '--i': index } as CSSProperties}
      onDragOver={(event) => {
        // Without preventDefault the browser never fires a drop event on this element.
        if (state === 'clear' || state === 'source') event.preventDefault()
      }}
      onDrop={(event) => {
        event.preventDefault()
        onDropHere()
      }}
      aria-label={STATUS_LABEL[status]}
    >
      <header className="column__head">
        <h2 className="column__name">{STATUS_LABEL[status]}</h2>
        <span className="column__count">{String(issues.length).padStart(2, '0')}</span>
      </header>
      <div className="column__signal" />

      {state === 'held' && holdReason && (
        <p className="column__hold" role="status">
          {holdReason}
        </p>
      )}

      <div className="column__cards">
        {issues.length === 0 && state !== 'held' && (
          <p className="column__empty">{EMPTY_TEXT[status]}</p>
        )}

        {issues.map((issue, cardIndex) => (
          <IssueCard
            key={issue.id}
            index={cardIndex}
            issue={issue}
            lifted={issue.id === liftedId}
            onLift={() => onLift(issue)}
            onDrop={onLiftEnd}
            onNudge={(direction) => onNudge(issue, direction)}
            onOpen={() => onOpen(issue)}
          />
        ))}

        {state === 'clear' && <div className="column__target">Drop to move here</div>}
      </div>
    </section>
  )
}
