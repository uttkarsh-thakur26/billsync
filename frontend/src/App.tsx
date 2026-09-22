import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react'
import { Link, Route, Routes } from 'react-router'
import { ACTING_USER_KEY, ActingContext, useActing, type Acting } from './acting'
import { createUser, deleteUser, errorMessage, listUsers, updateUser } from './api/client'
import type { UserResponse } from './api/types'
import GroupDetail from './pages/GroupDetail'
import Home from './pages/Home'
import SettleUp from './pages/SettleUp'
import { Alert, btn, btnSecondary, Empty, input } from './ui'

function readStoredActingUser(): number | null {
  try {
    const raw = localStorage.getItem(ACTING_USER_KEY)
    return raw ? Number(raw) : null
  } catch {
    return null
  }
}

/** Loads the people once and remembers who the person at the keyboard is. */
function ActingUserProvider({ children }: { children: ReactNode }) {
  const [users, setUsers] = useState<UserResponse[]>([])
  const [usersError, setUsersError] = useState<string>()
  const [actingUserId, setActingState] = useState<number | null>(readStoredActingUser)

  useEffect(() => {
    let cancelled = false
    listUsers().then(
      (list) => {
        if (!cancelled) setUsers(list)
      },
      (e: unknown) => {
        if (!cancelled) setUsersError(errorMessage(e))
      },
    )
    return () => {
      cancelled = true
    }
  }, [])

  const setActingUserId = useCallback((id: number | null) => {
    setActingState(id)
    try {
      if (id === null) localStorage.removeItem(ACTING_USER_KEY)
      else localStorage.setItem(ACTING_USER_KEY, String(id))
    } catch {
      // Storage blocked (private mode): the choice just will not survive a reload.
    }
  }, [])

  // Nothing stored, or the stored person no longer exists: act as the first one.
  const effectiveId = users.some((u) => u.id === actingUserId) ? actingUserId : (users[0]?.id ?? null)

  const value = useMemo<Acting>(
    () => ({
      users,
      usersError,
      addUser: (user) => setUsers((prev) => [...prev, user]),
      replaceUser: (user) => setUsers((prev) => prev.map((u) => (u.id === user.id ? user : u))),
      dropUser: (userId) => setUsers((prev) => prev.filter((u) => u.id !== userId)),
      actingUserId: effectiveId,
      setActingUserId,
      nameOf: (id) => {
        const name = users.find((u) => u.id === id)?.name ?? `User #${id}`
        return id === effectiveId ? `${name} (you)` : name
      },
    }),
    [users, usersError, effectiveId, setActingUserId],
  )

  return <ActingContext.Provider value={value}>{children}</ActingContext.Provider>
}

type PersonForm = { mode: 'add' } | { mode: 'edit'; user: UserResponse }

function Header() {
  const { users, usersError, addUser, replaceUser, dropUser, actingUserId, setActingUserId } = useActing()
  const [form, setForm] = useState<PersonForm | null>(null)
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string>()
  const acting = users.find((u) => u.id === actingUserId)

  function open(next: PersonForm) {
    setForm(next)
    setName(next.mode === 'edit' ? next.user.name : '')
    setEmail(next.mode === 'edit' ? next.user.email : '')
    setError(undefined)
  }

  async function run(action: () => Promise<void>) {
    setSaving(true)
    setError(undefined)
    try {
      await action()
      setForm(null)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!form) return
    void run(async () => {
      if (form.mode === 'add') {
        const user = await createUser({ name: name.trim(), email: email.trim() })
        addUser(user)
        setActingUserId(user.id)
      } else {
        replaceUser(await updateUser(form.user.id, { name: name.trim(), email: email.trim() }))
      }
    })
  }

  function remove(user: UserResponse) {
    if (!window.confirm(`Delete ${user.name}? This only works if they are in no group.`)) return
    void run(async () => {
      await deleteUser(user.id)
      dropUser(user.id)
    })
  }

  return (
    <header className="border-b border-gray-200 bg-white">
      <div className="mx-auto flex max-w-3xl flex-wrap items-center gap-3 p-4 sm:px-6">
        <Link to="/" className="text-lg font-bold tracking-tight">
          BillSync
        </Link>
        <div className="ml-auto flex items-center gap-2 text-sm">
          <label htmlFor="acting-as" className="text-gray-600">
            Acting as
          </label>
          <select
            id="acting-as"
            className={input}
            value={actingUserId ?? ''}
            onChange={(e) => setActingUserId(e.target.value ? Number(e.target.value) : null)}
          >
            {users.length === 0 && <option value="">No people yet</option>}
            {users.map((u) => (
              <option key={u.id} value={u.id}>
                {u.name}
              </option>
            ))}
          </select>
          {acting && (
            <button type="button" className={btnSecondary} onClick={() => open({ mode: 'edit', user: acting })}>
              Edit
            </button>
          )}
          <button
            type="button"
            className={btnSecondary}
            onClick={() => (form ? setForm(null) : open({ mode: 'add' }))}
          >
            {form ? 'Cancel' : '+ Person'}
          </button>
        </div>
        {form && (
          <form onSubmit={submit} className="flex w-full flex-wrap items-center gap-2">
            <input
              className={input}
              placeholder="Name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              maxLength={100}
              autoFocus
            />
            <input
              className={input}
              type="email"
              placeholder="Email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              maxLength={255}
            />
            <button className={btn} disabled={saving}>
              {saving ? 'Saving…' : form.mode === 'add' ? 'Add person' : 'Save'}
            </button>
            {form.mode === 'edit' && (
              <button
                type="button"
                className="text-sm text-red-600 hover:underline disabled:opacity-50"
                disabled={saving}
                onClick={() => remove(form.user)}
              >
                Delete {form.user.name}
              </button>
            )}
            <Alert message={error} />
          </form>
        )}
        {usersError && (
          <div className="w-full">
            <Alert message={usersError} />
          </div>
        )}
      </div>
    </header>
  )
}

export default function App() {
  return (
    <ActingUserProvider>
      <div className="min-h-screen bg-gray-50 text-gray-900">
        <Header />
        <main className="mx-auto max-w-3xl p-4 sm:p-6">
          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/groups/:groupId" element={<GroupDetail />} />
            <Route path="/groups/:groupId/settle" element={<SettleUp />} />
            <Route path="*" element={<Empty>Nothing here.</Empty>} />
          </Routes>
        </main>
      </div>
    </ActingUserProvider>
  )
}
