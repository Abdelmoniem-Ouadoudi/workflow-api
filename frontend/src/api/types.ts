/**
 * Mirrors the DTOs the backend publishes at /v3/api-docs.
 * When a DTO changes, this file changes with it — the spec is the source of truth.
 */

export type Status = 'TO_DO' | 'IN_PROGRESS' | 'DONE'
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type IssueType = 'BUG' | 'FEATURE' | 'SUPPORT' | 'TASK'
export type BoardType = 'KANBAN' | 'SCRUM'
export type Role = 'DEVELOPER' | 'MANAGER' | 'ADMIN'

export const STATUSES: Status[] = ['TO_DO', 'IN_PROGRESS', 'DONE']
export const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
export const ISSUE_TYPES: IssueType[] = ['BUG', 'FEATURE', 'SUPPORT', 'TASK']

/** Column headings. The enum names are for the wire, not for people. */
export const STATUS_LABEL: Record<Status, string> = {
  TO_DO: 'To do',
  IN_PROGRESS: 'In progress',
  DONE: 'Done',
}

export interface Project {
  id: number
  key: string
  name: string
  description: string | null
  createdAt: string
}

export interface Board {
  id: number
  name: string
  type: BoardType
  projectId: number
  createdAt: string
}

export interface User {
  id: number
  username: string
  email: string
  role: Role
  active: boolean
  createdAt: string
}

export interface IssueSummary {
  id: number
  issueKey: string
  title: string
  type: IssueType
  status: Status
  priority: Priority
  assigneeId: number | null
  assigneeUsername: string | null
  dueDate: string | null
  version: number
}

export interface BoardColumn {
  status: Status
  issues: IssueSummary[]
}

export interface BoardView {
  boardId: number
  boardName: string
  type: BoardType
  activeSprint: { id: number; name: string; goal: string | null } | null
  columns: BoardColumn[]
}

/** What PATCH /issues/{id}/status returns. */
export interface Issue {
  id: number
  issueKey: string
  title: string
  status: Status
  priority: Priority
  version: number
}

export interface FieldError {
  field: string
  message: string
}

/** The one error shape every failing request returns. */
export interface ApiErrorBody {
  timestamp: string
  status: number
  code: string
  message: string
  path: string
  fieldErrors?: FieldError[]
}
