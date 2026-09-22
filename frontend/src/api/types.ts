/**
 * Mirrors of the backend DTOs in com.uttkarsh.billsync.dto, one interface per record,
 * same names and same field names. When a DTO changes, change it here too: the
 * compiler then points at every screen that needs updating.
 */

export type SplitType = 'EQUAL' | 'EXACT' | 'PERCENTAGE'

/**
 * Money (and percentages) travel as strings: "33.34", never 33.34. The backend
 * serialises BigDecimal that way on purpose, so a JavaScript double never gets
 * a chance to turn 0.1 + 0.2 into 0.30000000000000004.
 */
export type Money = string

/** ISO-8601 instant, e.g. "2026-09-21T20:15:00Z". */
export type Timestamp = string

export interface CreateUserRequest {
  name: string
  email: string
}

export interface UserResponse {
  id: number
  name: string
  email: string
  createdAt: Timestamp
}

export interface CreateGroupRequest {
  name: string
  memberUserIds?: number[]
}

export interface AddMemberRequest {
  userId: number
}

export interface GroupResponse {
  id: number
  name: string
  createdAt: Timestamp
  members: UserResponse[]
}

export interface CreateExpenseRequest {
  paidByUserId: number
  amount: Money
  description: string
  splitType: SplitType
  participantUserIds: number[]
  /** EXACT: amount per participant. PERCENTAGE: percentage per participant. Keyed by user id. */
  splitValues?: Record<number, Money>
}

export interface ShareResponse {
  userId: number
  amountOwed: Money
}

export interface ExpenseResponse {
  id: number
  groupId: number
  paidByUserId: number
  amount: Money
  description: string
  splitType: SplitType
  shares: ShareResponse[]
  createdAt: Timestamp
}

export interface MemberBalanceResponse {
  userId: number
  /** Positive: the group owes them. Negative: they owe the group. */
  balance: Money
}

export interface BalancesResponse {
  groupId: number
  balances: MemberBalanceResponse[]
}

export interface TransactionResponse {
  fromUserId: number
  toUserId: number
  amount: Money
}

export interface SettlementPlanResponse {
  groupId: number
  transactionCount: number
  naiveTransactionCount: number
  transactions: TransactionResponse[]
}

export interface RecordSettlementRequest {
  fromUserId: number
  toUserId: number
  amount: Money
}

export interface SettlementResponse {
  id: number
  groupId: number
  fromUserId: number
  toUserId: number
  amount: Money
  settledAt: Timestamp
}

/** The one error shape every endpoint returns. */
export interface ApiErrorBody {
  timestamp: Timestamp
  status: number
  error: string
  details: string[]
}
