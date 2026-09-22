import type {
  AddMemberRequest,
  ApiErrorBody,
  BalancesResponse,
  CreateExpenseRequest,
  CreateGroupRequest,
  CreateUserRequest,
  ExpenseResponse,
  GroupResponse,
  RecordSettlementRequest,
  SettlementPlanResponse,
  SettlementResponse,
  UserResponse,
} from './types'

const BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '')

/** Thrown for every non-2xx response and for network failures. `status` is 0 when the API was unreachable. */
export class ApiError extends Error {
  readonly status: number
  readonly details: string[]

  constructor(status: number, details: string[], message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.details = details
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${BASE_URL}/api${path}`, {
      method,
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, [], `Cannot reach the BillSync API at ${BASE_URL}. Is the backend running?`)
  }

  const text = await response.text()
  let data: unknown = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      // Not JSON (a proxy error page, say). Fall through with the status alone.
    }
  }

  if (!response.ok) {
    const error = data as Partial<ApiErrorBody> | null
    const details = error?.details ?? []
    throw new ApiError(response.status, details, details[0] ?? error?.error ?? `Request failed with HTTP ${response.status}`)
  }
  return data as T
}

// One function per endpoint, in the order of the API table.

export const listUsers = () => request<UserResponse[]>('GET', '/users')
export const createUser = (body: CreateUserRequest) => request<UserResponse>('POST', '/users', body)

export const listGroups = () => request<GroupResponse[]>('GET', '/groups')
export const createGroup = (body: CreateGroupRequest) => request<GroupResponse>('POST', '/groups', body)
export const getGroup = (groupId: number) => request<GroupResponse>('GET', `/groups/${groupId}`)
export const addMember = (groupId: number, body: AddMemberRequest) =>
  request<GroupResponse>('POST', `/groups/${groupId}/members`, body)

export const listExpenses = (groupId: number) => request<ExpenseResponse[]>('GET', `/groups/${groupId}/expenses`)
export const addExpense = (groupId: number, body: CreateExpenseRequest) =>
  request<ExpenseResponse>('POST', `/groups/${groupId}/expenses`, body)
export const deleteExpense = (expenseId: number) => request<void>('DELETE', `/expenses/${expenseId}`)

export const getBalances = (groupId: number) => request<BalancesResponse>('GET', `/groups/${groupId}/balances`)
export const getSettlementPlan = (groupId: number) =>
  request<SettlementPlanResponse>('GET', `/groups/${groupId}/settlement-plan`)
export const recordSettlement = (groupId: number, body: RecordSettlementRequest) =>
  request<SettlementResponse>('POST', `/groups/${groupId}/settlements`, body)

/** Message to show a person for any thrown value. */
export function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : String(e)
}
