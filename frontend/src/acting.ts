import { createContext, useContext } from 'react'
import type { UserResponse } from './api/types'

/** localStorage key for the selected "acting as" user. */
export const ACTING_USER_KEY = 'billsync.actingUserId'

export interface Acting {
  users: UserResponse[]
  usersError?: string
  /** Keep the local list in step with creates, renames and deletes, without a round trip. */
  addUser: (user: UserResponse) => void
  replaceUser: (user: UserResponse) => void
  dropUser: (userId: number) => void
  actingUserId: number | null
  setActingUserId: (id: number | null) => void
  /** Display name for an id, with "(you)" appended for the acting user. */
  nameOf: (userId: number) => string
}

export const ActingContext = createContext<Acting | null>(null)

export function useActing(): Acting {
  const acting = useContext(ActingContext)
  if (!acting) throw new Error('useActing must be used inside <ActingUserProvider>')
  return acting
}
