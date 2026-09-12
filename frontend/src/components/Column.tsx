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
    // Two classes: the status gives the column its tint, the state says whether a lifted card
    // may land here. They are independent, so neither is folded into the other.
    <section
      className={`column column--${status} column--${state}`}
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
        <h2>{STATUS_LABEL[status]}</h2>
        <span>{issues.length}</span>
      </header>

      {state === 'held' && holdReason && (
        <p className="column__hold" role="status">
          {holdReason}
        </p>
      )}

      <div className="column__cards">
        {issues.length === 0 && state !== 'held' && (
          <p className="column__empty">{EMPTY_TEXT[status]}</p>
        )}

        {issues.map((issue) => (
          <IssueCard
            key={issue.id}
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
