import type {
  ApiErrorBody,
  Board,
  BoardView,
  FieldError,
  Issue,
  IssueType,
  Priority,
  Project,
  Status,
  User,
} from './types'

const BASE_URL = 'http://localhost:8081'

/**
 * Every failing request throws this. The backend already returns one envelope for every
 * failure, so the parsing happens once here instead of in each component.
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

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      ...init,
      headers: { 'Content-Type': 'application/json', ...init?.headers },
    })
  } catch {
    // fetch only rejects when the request never completed: server down, DNS, CORS block.
    throw new ApiError({
      timestamp: new Date().toISOString(),
      status: 0,
      code: 'UNREACHABLE',
      message: 'Cannot reach the server. Check that work-service is running on port 8081.',
      path,
    })
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

export interface NewIssue {
  title: string
  type: IssueType
  priority: Priority
  projectId: number
  reporterId: number
  boardId: number
}

export const api = {
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
}
