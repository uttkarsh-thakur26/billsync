import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { useActing } from '../acting'
import { createGroup, errorMessage, listGroups } from '../api/client'
import { Alert, btn, card, Empty, heading, input, Loading } from '../ui'
import { useLoad } from '../useLoad'

export default function Home() {
  const { users, actingUserId, nameOf } = useActing()
  const groups = useLoad(listGroups, [])
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [creating, setCreating] = useState(false)
  const [error, setError] = useState<string>()

  async function create(e: FormEvent) {
    e.preventDefault()
    setCreating(true)
    setError(undefined)
    try {
      const group = await createGroup({
        name: name.trim(),
        memberUserIds: actingUserId === null ? [] : [actingUserId],
      })
      navigate(`/groups/${group.id}`)
    } catch (err) {
      setError(errorMessage(err))
      setCreating(false)
    }
  }

  return (
    <div className="space-y-6">
      <form onSubmit={create} className={`${card} flex flex-wrap items-end gap-2`}>
        <label className="min-w-48 flex-1 text-sm">
          <span className="mb-1 block font-medium">New group</span>
          <input
            className={`${input} w-full`}
            placeholder="Goa trip"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            maxLength={120}
          />
        </label>
        <button className={btn} disabled={creating || name.trim() === ''}>
          {creating ? 'Creating…' : 'Create group'}
        </button>
        <p className="w-full text-xs text-gray-500">
          {actingUserId === null
            ? 'Add a person in the header first so someone can be in the group.'
            : `${nameOf(actingUserId)} will be its first member.`}
        </p>
        <Alert message={error} />
      </form>

      <section>
        <h2 className={heading}>Groups</h2>
        <Alert message={groups.error} />
        {!groups.data && groups.loading && <Loading what="groups" />}
        {groups.data?.length === 0 && <Empty>No groups yet. Create one above.</Empty>}
        {groups.data && groups.data.length > 0 && (
          <ul className="space-y-2">
            {groups.data.map((g) => (
              <li key={g.id}>
                <Link to={`/groups/${g.id}`} className={`${card} block hover:border-gray-400`}>
                  <div className="flex items-baseline justify-between gap-3">
                    <span className="font-medium">{g.name}</span>
                    <span className="text-xs text-gray-500">{new Date(g.createdAt).toLocaleDateString()}</span>
                  </div>
                  <p className="mt-1 text-sm text-gray-600">
                    {g.members.length === 0
                      ? 'No members yet'
                      : // Names from the live user list, so a rename in the header shows here at once.
                        g.members.map((m) => users.find((u) => u.id === m.id)?.name ?? m.name).join(', ')}
                  </p>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
