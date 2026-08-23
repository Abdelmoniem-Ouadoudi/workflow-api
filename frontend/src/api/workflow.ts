import type { Status } from './types'

/**
 * A copy of IssueService.ALLOWED_TRANSITIONS.
 *
 * The server stays the authority — it rejects an illegal move with 422 whatever the browser
 * believes. This copy exists so the board can show which columns will accept a card *while it
 * is being dragged*, instead of letting someone drop it and then explaining the refusal.
 *
 * If the rule changes on the server, change it here too.
 */
const ALLOWED: Record<Status, Status[]> = {
  TO_DO: ['IN_PROGRESS'],
  IN_PROGRESS: ['TO_DO', 'DONE'],
  DONE: ['IN_PROGRESS'],
}

export function canMove(from: Status, to: Status): boolean {
  return from === to || ALLOWED[from].includes(to)
}

/**
 * Why a column will not take the card. Shown on the held column during the drag,
 * so the rule is learned rather than enforced after the fact.
 */
export function holdReason(from: Status, to: Status): string {
  if (from === 'TO_DO' && to === 'DONE') {
    return 'Work has to start before it can finish.'
  }
  if (from === 'DONE' && to === 'TO_DO') {
    return 'Reopen it first, then send it back.'
  }
  return 'This move is not part of the workflow.'
}
