/**
 * Mirrors the DTOs the backend publishes at /v3/api-docs.
 * When a DTO changes, this file changes with it — the spec is the source of truth.
 */

export type Status = 'TO_DO' | 'IN_PROGRESS' | 'DONE'
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type IssueType = 'BUG' | 'FEATURE' | 'SUPPORT' | 'TASK'
export type BoardType = 'KANBAN' | 'SCRUM'
export type Role = 'DEVELOPER' | 'MANAGER' | 'ADMIN'

/** What somebody is inside one project. Not the same axis as Role, which is platform-wide. */
export type ProjectRole = 'PROJECT_MANAGER' | 'MEMBER'

/** Whether an account may log in, and why not when it may not. */
export type AccountStatus = 'PENDING' | 'ACTIVE' | 'DISABLED'

export type JoinRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export const ROLES: Role[] = ['DEVELOPER', 'MANAGER', 'ADMIN']

/** What each global role actually gets you. Shown next to the choice on the approval screen. */
export const ROLE_LABEL: Record<Role, string> = {
  DEVELOPER: 'Developer — joins projects, cannot start one',
  MANAGER: 'Manager — can create projects and runs the ones they create',
  ADMIN: 'Administrator — approves accounts and sees every project',
}

export const PROJECT_ROLE_LABEL: Record<ProjectRole, string> = {
  PROJECT_MANAGER: 'Project manager',
  MEMBER: 'Member',
}

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

/** What PUT /issues/{id}/assignee returns — the full IssueDTO, we only read these three. */
export interface IssueAssignment {
  id: number
  assigneeId: number | null
  version: number
}

export interface IssueComment {
  id: number
  content: string
  issueId: number
  authorId: number
  createdAt: string
  updatedAt: string
}

export type Effort = 'SMALL' | 'MEDIUM' | 'LARGE'
export type ReviewStatus = 'PENDING' | 'AUTO_APPLIED' | 'CONFIRMED' | 'OVERRIDDEN'

/**
 * What the model thought about one issue.
 *
 * Every suggested field is nullable: a model with no opinion returns nothing rather than guessing,
 * and the chip renders that as an absent row instead of an empty one.
 *
 * `issueVersion` is the issue's optimistic-locking version at the time of reading. When a
 * suggestion is auto-applied the issue really did change, so the board has to take this value or
 * the next drag on that card sends a stale version and gets a false 409.
 */
export interface AIClassification {
  id: number
  issueId: number
  suggestedType: IssueType | null
  suggestedPriority: Priority | null
  suggestedTeam: string | null
  effortHint: Effort | null
  sentimentScore: number | null
  confidence: number
  missingInfo: string[] | null
  reviewStatus: ReviewStatus
  /** Which model said this. `stub-v1` means keyword rules, not AI. */
  modelVersion: string
  issueVersion: number
  createdAt: string
}

/**
 * A ticket that already says roughly what you are typing.
 *
 * Everything here comes out of the vector's own metadata, so the search is one hop and does not
 * wait on work-service.
 */
export interface SimilarIssue {
  issueId: number
  issueKey: string
  title: string
  projectKey: string
  /** 0 to 1, where 1 is identical. Shown as a percentage so a borderline match can be judged. */
  score: number
}

/** One bar on a chart. The same shape for every distribution, so one component draws them all. */
export interface CountByLabel {
  label: string
  count: number
}

export interface Dashboard {
  totalIssues: number
  classifiedIssues: number
  /** Suggestions nobody has accepted or rejected yet. The queue of human work. */
  awaitingReview: number
  /**
   * Percentage, or null when nobody has judged a suggestion yet. Null and 0 mean very different
   * things here — "nobody has checked" versus "the AI is always wrong".
   */
  aiAgreementRate: number | null
  byType: CountByLabel[]
  byPriority: CountByLabel[]
  byStatus: CountByLabel[]
  /** From what the AI read out of each ticket. There is no team field on an issue. */
  byTeam: CountByLabel[]
  byEffort: CountByLabel[]
}

/** One person on a project, flattened: every column the members table draws. */
export interface ProjectMember {
  userId: number
  username: string
  email: string
  globalRole: Role
  role: ProjectRole
  active: boolean
  joinedAt: string
}

/** What a join code buys before anybody has approved anything: enough to confirm the project. */
export interface ProjectLookup {
  id: number
  key: string
  name: string
}

export interface JoinRequest {
  id: number
  projectId: number
  projectKey: string
  projectName: string
  userId: number
  username: string
  email: string
  status: JoinRequestStatus
  requestedAt: string
  decidedAt: string | null
}

/**
 * One row of the administrator's screen: half from `account` in authdb, half from `app_user` in
 * work-service. `email` is null when work-service could not be reached, or when a registration
 * half-failed and left an account whose profile was never created.
 */
export interface AdminAccount {
  id: number
  workUserId: number
  username: string
  email: string | null
  role: Role
  status: AccountStatus
  createdAt: string
}

/**
 * What POST /auth/register returns now: an acknowledgement, not a token.
 *
 * Registering no longer signs you in, because it no longer lets you in — an administrator has to
 * approve the account first.
 */
export interface RegistrationReceipt {
  username: string
  status: AccountStatus
  message: string
}

/** What POST /auth/login returns. Registration no longer returns one. */
export interface TokenResponse {
  token: string
  expiresAt: string
  /**
   * The work-service user id, not the account id. It is what an issue stores as its reporter,
   * so it is the only id the rest of the app ever needs.
   */
  userId: number
  username: string
  role: Role
}

/** What GET /auth/me returns: the signed-in user, read back off the token. */
export interface CurrentUser {
  userId: number
  username: string
  role: Role
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
