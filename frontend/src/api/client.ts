import { endSession, getSession, startSession } from '../auth/session'
import type {
  AIClassification,
  ApiErrorBody,
  Board,
  BoardView,
  CurrentUser,
  FieldError,
  Issue,
  IssueAssignment,
  IssueComment,
  IssueType,
  Priority,
  Project,
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
  } catch {
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
  role: Role
}

export const api = {
  register: (registration: Registration) =>
    request<TokenResponse>('/auth/register', {
      method: 'POST',
      body: JSON.stringify(registration),
      anonymous: true,
    }).then(startSession),

  login: (credentials: Credentials) =>
    request<TokenResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify(credentials),
      anonymous: true,
    }).then(startSession),

  /** Confirms a stored token is still accepted before the app renders a board with it. */
  me: () => request<CurrentUser>('/auth/me'),

  listProjects: () => request<Project[]>('/projects'),

  createProject: (project: NewProject) =>
    request<Project>('/projects', { method: 'POST', body: JSON.stringify(project) }),

  listBoards: (projectId: number) => request<Board[]>(`/boards?projectId=${projectId}`),

  boardView: (boardId: number) => request<BoardView>(`/boards/${boardId}/view`),

  listUsers: () => request<User[]>('/users'),

  createIssue: (issue: NewIssue) =>
    request<Issue>('/issues', { method: 'POST', body: JSON.stringify(issue) }),

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
