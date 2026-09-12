import { endSession, getSession, startSession } from '../auth/session'
import type {
  AccountStatus,
  AdminAccount,
  AIClassification,
  ApiErrorBody,
  Board,
  BoardView,
  CurrentUser,
  Dashboard,
  SimilarIssue,
  FieldError,
  Issue,
  IssueAssignment,
  IssueComment,
  IssueDetail,
  IssueStatusChange,
  IssueType,
  JoinRequest,
  Priority,
  Project,
  ProjectLookup,
  ProjectMember,
  ProjectRole,
  RegistrationReceipt,
  Role,
  Status,
  TokenResponse,
  User,
} from './types'

/**
 * The gateway, not work-service. Since M2 the browser knows exactly one address: the gateway
 * routes to auth-service or work-service by path, asks Eureka where they are, and refuses
 * anything without a valid token before it reaches either of them.
 */
const BASE_URL = 'http://localhost:8090'

/**
 * Every failing request throws this. Every service in the system returns one envelope — including
 * the gateway when a service is down — so the parsing happens once here instead of per component.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: FieldError[]

  constructor(body: ApiErrorBody) {
    super(body.message)
    this.name = 'ApiError'
    this.status = body.status
    this.code = body.code
    this.fieldErrors = body.fieldErrors ?? []
  }

  /** Message for a specific form input, or undefined. */
  forField(field: string): string | undefined {
    return this.fieldErrors.find((e) => e.field === field)?.message
  }
}

/** Called when the server says the session is no longer good. App re-renders on the login screen. */
type ExpiryListener = () => void
let onSessionExpired: ExpiryListener = () => {}

export function setSessionExpiredHandler(listener: ExpiryListener): void {
  onSessionExpired = listener
}

interface RequestOptions extends RequestInit {
  /** Login and register are the two calls made without a token. */
  anonymous?: boolean
}

async function request<T>(path: string, options?: RequestOptions): Promise<T> {
  const { anonymous, ...init } = options ?? {}
  const session = getSession()

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(init.headers as Record<string, string> | undefined),
  }
  if (!anonymous && session !== null) {
    headers.Authorization = `Bearer ${session.token}`
  }

  let response: Response
  try {
    response = await fetch(`${BASE_URL}${path}`, { ...init, headers })
  } catch (cause) {
    // An abort is not a failure — it is this code cancelling a request it no longer wants, which
    // the duplicate search does on every keystroke. Rethrown as-is so callers can ignore it;
    // dressing it up as UNREACHABLE would put "cannot reach the server" on screen while typing.
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw cause
    }
    // fetch only rejects when the request never completed: gateway down, DNS, CORS block.
    throw new ApiError({
      timestamp: new Date().toISOString(),
      status: 0,
      code: 'UNREACHABLE',
      message: 'Cannot reach the server. Check that the gateway is running on port 8090.',
      path,
    })
  }

  // The token expired, or it was never valid. Anything else would have been a 403.
  // Clearing here rather than in each component means one expiry rule for the whole app.
  if (response.status === 401 && !anonymous) {
    endSession()
    onSessionExpired()
  }

  if (response.status === 204) {
    return undefined as T
  }

  const body = await response.json().catch(() => null)

  if (!response.ok) {
    throw new ApiError(
      (body as ApiErrorBody | null) ?? {
        timestamp: new Date().toISOString(),
        status: response.status,
        code: 'UNKNOWN',
        message: `Request failed with status ${response.status}.`,
        path,
      },
    )
  }

  return body as T
}

export interface NewProject {
  key: string
  name: string
  description?: string
}

/**
 * No `reporterId`. Since M2 the server takes the reporter from the token, so sending one would
 * be ignored — and before M2 it meant anyone could file an issue in someone else's name.
 */
export interface NewIssue {
  title: string
  type: IssueType
  priority: Priority
  projectId: number
  boardId: number
}

export interface Credentials {
  username: string
  password: string
}

export interface Registration extends Credentials {
  email: string
}

export const api = {
  /**
   * No `.then(startSession)` any more, and no token in the reply.
   *
   * Registering creates a PENDING account that cannot log in until an administrator approves it,
   * so there is no session to start. The role is gone from the body too — it used to be sent from
   * here, which meant anybody could register themselves as an administrator.
   */
  register: (registration: Registration) =>
    request<RegistrationReceipt>('/auth/register', {
      method: 'POST',
      body: JSON.stringify(registration),
      anonymous: true,
    }),

  login: (credentials: Credentials) =>
    request<TokenResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify(credentials),
      anonymous: true,
    }).then(startSession),

  /** Confirms a stored token is still accepted before the app renders a board with it. */
  me: () => request<CurrentUser>('/auth/me'),

  /**
   * Tickets that already say roughly this. Answers while somebody is still typing, so it takes an
   * AbortSignal: a slow reply for text that has since changed must not overwrite a newer one.
   */
  similarIssues: (text: string, projectKey: string, signal: AbortSignal) =>
    request<SimilarIssue[]>(
      `/similar?text=${encodeURIComponent(text)}&projectKey=${encodeURIComponent(projectKey)}`,
      { signal },
    ),

  dashboard: () => request<Dashboard>('/dashboard'),

  /** Pushes every issue back through the classifier. ADMIN only. */
  reindexIssues: () => request<{ queued: number }>('/admin/issues/reindex', { method: 'POST' }),

  listProjects: () => request<Project[]>('/projects'),

  /** 403 if you are not on it — which is how a bookmarked board URL is refused. */
  getProject: (id: number) => request<Project>(`/projects/${id}`),

  createProject: (project: NewProject) =>
    request<Project>('/projects', { method: 'POST', body: JSON.stringify(project) }),

  listBoards: (projectId: number) => request<Board[]>(`/boards?projectId=${projectId}`),

  boardView: (boardId: number) => request<BoardView>(`/boards/${boardId}/view`),

  /**
   * ADMIN only since M5 — it returns every user's email address. The board's assignee dropdown
   * used to call this; it calls `projectMembers` now, which is both narrower and more correct,
   * because work can only be given to somebody who is on the project.
   */
  listUsers: () => request<User[]>('/users'),

  // ----- Project membership: the chef de projet's screen -----

  projectMembers: (projectId: number) =>
    request<ProjectMember[]>(`/projects/${projectId}/members`),

  addProjectMember: (projectId: number, userId: number, role: ProjectRole) =>
    request<ProjectMember>(`/projects/${projectId}/members`, {
      method: 'POST',
      body: JSON.stringify({ userId, role }),
    }),

  changeProjectMemberRole: (projectId: number, userId: number, role: ProjectRole) =>
    request<ProjectMember>(`/projects/${projectId}/members/${userId}`, {
      method: 'PUT',
      body: JSON.stringify({ role }),
    }),

  removeProjectMember: (projectId: number, userId: number) =>
    request<void>(`/projects/${projectId}/members/${userId}`, { method: 'DELETE' }),

  /** The secret to mail somebody. Only a project manager may read it. */
  joinCode: (projectId: number) =>
    request<{ joinCode: string }>(`/projects/${projectId}/join-code`),

  /** A new code. Current members stay members and open requests stay open. */
  rotateJoinCode: (projectId: number) =>
    request<{ joinCode: string }>(`/projects/${projectId}/join-code/rotate`, { method: 'POST' }),

  // ----- Joining a project with a code somebody mailed you -----

  /**
   * POST for a read, deliberately. The code is a secret, and a query string lands in browser
   * history, in the gateway's access log, and in a Referer header on the next request.
   */
  lookupProject: (joinCode: string) =>
    request<ProjectLookup>('/projects/lookup', {
      method: 'POST',
      body: JSON.stringify({ joinCode }),
    }),

  requestToJoin: (projectId: number, joinCode: string) =>
    request<JoinRequest>(`/projects/${projectId}/join-requests`, {
      method: 'POST',
      body: JSON.stringify({ joinCode }),
    }),

  myJoinRequests: () => request<JoinRequest[]>('/projects/join-requests/mine'),

  projectJoinRequests: (projectId: number) =>
    request<JoinRequest[]>(`/projects/${projectId}/join-requests`),

  approveJoinRequest: (projectId: number, requestId: number) =>
    request<JoinRequest>(`/projects/${projectId}/join-requests/${requestId}/approve`, {
      method: 'POST',
    }),

  rejectJoinRequest: (projectId: number, requestId: number) =>
    request<JoinRequest>(`/projects/${projectId}/join-requests/${requestId}/reject`, {
      method: 'POST',
    }),

  // ----- The administrator's console. Served by auth-service, routed through the gateway. -----

  adminAccounts: (status?: AccountStatus) =>
    request<AdminAccount[]>(`/admin/accounts${status === undefined ? '' : `?status=${status}`}`),

  approveAccount: (id: number, role: Role) =>
    request<AdminAccount>(`/admin/accounts/${id}/approve`, {
      method: 'POST',
      body: JSON.stringify({ role }),
    }),

  rejectAccount: (id: number) =>
    request<AdminAccount>(`/admin/accounts/${id}/reject`, { method: 'POST' }),

  changeAccountRole: (id: number, role: Role) =>
    request<AdminAccount>(`/admin/accounts/${id}/role`, {
      method: 'PUT',
      body: JSON.stringify({ role }),
    }),

  enableAccount: (id: number) =>
    request<AdminAccount>(`/admin/accounts/${id}/enable`, { method: 'POST' }),

  disableAccount: (id: number) =>
    request<AdminAccount>(`/admin/accounts/${id}/disable`, { method: 'POST' }),

  createIssue: (issue: NewIssue) =>
    request<Issue>('/issues', { method: 'POST', body: JSON.stringify(issue) }),

  /** One issue in full. 403 for a project you are not on, 404 for an id that does not exist. */
  getIssue: (id: number) => request<IssueDetail>(`/issues/${id}`),

  /**
   * `version` is the value the board last saw. If the row moved on since then the server
   * answers 409 instead of overwriting the other person's change.
   */
  moveIssue: (id: number, status: Status, version: number) =>
    request<Issue>(`/issues/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status, version }),
    }),

  /** Pass null to unassign — the query param is simply omitted. */
  assignIssue: (issueId: number, userId: number | null) =>
    request<IssueAssignment>(
      `/issues/${issueId}/assignee${userId === null ? '' : `?userId=${userId}`}`,
      { method: 'PUT' },
    ),

  /**
   * The suggestion, or null while the classifier has not answered yet.
   *
   * The server says 204 for "not ready", which `request` turns into undefined. That is the state
   * the chip polls on — a 404 would mean the URL is wrong, which is a different problem.
   */
  classification: (issueId: number) =>
    request<AIClassification | undefined>(`/issues/${issueId}/classification`).then(
      (found) => found ?? null,
    ),

  acceptClassification: (issueId: number) =>
    request<AIClassification>(`/issues/${issueId}/classification/accept`, { method: 'POST' }),

  overrideClassification: (issueId: number) =>
    request<AIClassification>(`/issues/${issueId}/classification/override`, { method: 'POST' }),

  listComments: (issueId: number) => request<IssueComment[]>(`/issues/${issueId}/comments`),

  /**
   * Every move this card has made, oldest first. Read-only: the rows are written by the move
   * itself, so there is no post, edit or delete to call here.
   */
  listHistory: (issueId: number) => request<IssueStatusChange[]>(`/issues/${issueId}/history`),

  /** No author is sent: the server reads it from the token. */
  createComment: (issueId: number, content: string) =>
    request<IssueComment>(`/issues/${issueId}/comments`, {
      method: 'POST',
      body: JSON.stringify({ content }),
    }),

  updateComment: (issueId: number, commentId: number, content: string) =>
    request<IssueComment>(`/issues/${issueId}/comments/${commentId}`, {
      method: 'PUT',
      body: JSON.stringify({ content }),
    }),

  deleteComment: (issueId: number, commentId: number) =>
    request<void>(`/issues/${issueId}/comments/${commentId}`, { method: 'DELETE' }),
}
