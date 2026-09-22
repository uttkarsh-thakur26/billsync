import { createContext, useContext } from 'react'
import type { UserResponse } from './api/types'

/** localStorage key for the selected "acting as" user. */
export const ACTING_USER_KEY = 'billsync.actingUserId'

export interface Acting {
  users: UserResponse[]
  usersError?: string
  /** Add a freshly created user to the list without a round trip. */
  addUser: (user: UserResponse) => void
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
