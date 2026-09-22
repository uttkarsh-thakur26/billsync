import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { useActing } from '../acting'
import { addMember, deleteExpense, errorMessage, getBalances, getGroup, listExpenses, removeMember } from '../api/client'
import type { ExpenseResponse } from '../api/types'
import { rupees, sign } from '../money'
import { Alert, btn, btnSecondary, card, Empty, heading, input, Loading } from '../ui'
import { useLoad } from '../useLoad'
import AddExpenseModal from './AddExpenseModal'

export default function GroupDetail() {
  const groupId = Number(useParams().groupId)
  const { users, nameOf } = useActing()
  const page = useLoad(
    () => Promise.all([getGroup(groupId), listExpenses(groupId), getBalances(groupId)]),
    [groupId],
  )
  const [showAdd, setShowAdd] = useState(false)
  const [memberToAdd, setMemberToAdd] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  if (!page.data) {
    return page.error ? (
      <div className="space-y-3">
        <Link to="/" className="text-sm text-gray-500 hover:underline">
          ← Groups
        </Link>
        <Alert message={page.error} />
      </div>
    ) : (
      <Loading what="group" />
    )
  }
  const [group, expenses, balances] = page.data
  const memberIds = new Set(group.members.map((m) => m.id))
  const candidates = users.filter((u) => !memberIds.has(u.id))

  async function run(action: () => Promise<unknown>) {
    setBusy(true)
    setError(undefined)
    try {
      await action()
      page.reload()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  function remove(expense: ExpenseResponse) {
    if (!window.confirm(`Delete "${expense.description}" (${rupees(expense.amount)})?`)) return
    void run(() => deleteExpense(expense.id))
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-3">
        <Link to="/" className="text-sm text-gray-500 hover:underline">
          ← Groups
        </Link>
        <h1 className="text-2xl font-bold">{group.name}</h1>
        <div className="ml-auto flex gap-2">
          <Link to={`/groups/${groupId}/settle`} className={btnSecondary}>
            Settle up
          </Link>
          <button className={btn} onClick={() => setShowAdd(true)} disabled={group.members.length === 0}>
            Add expense
          </button>
        </div>
      </div>
      <Alert message={error ?? page.error} />

      <section className={card}>
        <h2 className={heading}>Members</h2>
        {group.members.length === 0 ? (
          <Empty>No members yet.</Empty>
        ) : (
          <ul className="flex flex-wrap gap-2">
            {group.members.map((m) => (
              <li key={m.id} className="flex items-center gap-1 rounded-full bg-gray-100 py-1 pr-2 pl-3 text-sm">
                {nameOf(m.id)}
                <button
                  type="button"
                  className="rounded-full px-1 leading-none text-gray-400 hover:bg-gray-200 hover:text-gray-700 disabled:opacity-50"
                  title={`Remove ${m.name} from the group`}
                  aria-label={`Remove ${m.name} from the group`}
                  disabled={busy}
                  onClick={() => {
                    if (!window.confirm(`Remove ${m.name} from ${group.name}?`)) return
                    void run(() => removeMember(groupId, m.id))
                  }}
                >
                  ×
                </button>
              </li>
            ))}
          </ul>
        )}
        <div className="mt-3 flex items-center gap-2">
          {candidates.length === 0 ? (
            <p className="text-xs text-gray-500">
              {users.length === 0 ? 'Add a person from the header to get started.' : 'Everyone is in this group.'}
            </p>
          ) : (
            <>
              <select
                className={input}
                value={memberToAdd}
                onChange={(e) => setMemberToAdd(e.target.value)}
                aria-label="Person to add"
              >
                <option value="">Add a member…</option>
                {candidates.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.name}
                  </option>
                ))}
              </select>
              <button
                className={btnSecondary}
                disabled={memberToAdd === '' || busy}
                onClick={() =>
                  void run(async () => {
                    await addMember(groupId, { userId: Number(memberToAdd) })
                    setMemberToAdd('')
                  })
                }
              >
                Add
              </button>
            </>
          )}
        </div>
      </section>

      <section className={card}>
        <h2 className={heading}>Balances</h2>
        {balances.balances.length === 0 ? (
          <Empty>No members, so no balances.</Empty>
        ) : (
          <ul className="divide-y divide-gray-100">
            {balances.balances.map((b) => {
              const s = sign(b.balance)
              return (
                <li key={b.userId} className="flex items-center justify-between py-2 text-sm">
                  <span>{nameOf(b.userId)}</span>
                  {s > 0 && <span className="font-medium text-green-700">is owed {rupees(b.balance)}</span>}
                  {s < 0 && <span className="font-medium text-red-700">owes {rupees(b.balance.slice(1))}</span>}
                  {s === 0 && <span className="text-gray-500">settled up</span>}
                </li>
              )
            })}
          </ul>
        )}
      </section>

      <section>
        <h2 className={heading}>Expenses</h2>
        {expenses.length === 0 ? (
          <Empty>No expenses yet. Add the first one.</Empty>
        ) : (
          <ul className="space-y-2">
            {expenses.map((e) => (
              <li key={e.id} className={card}>
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="font-medium">{e.description}</p>
                    <p className="text-sm text-gray-600">
                      {nameOf(e.paidByUserId)} paid <span className="font-medium text-gray-900">{rupees(e.amount)}</span>
                      {' · '}split {e.splitType.toLowerCase()}
                      {' · '}
                      {new Date(e.createdAt).toLocaleDateString()}
                    </p>
                    <p className="mt-1 flex flex-wrap gap-1 text-xs text-gray-600">
                      {e.shares.map((s) => (
                        <span key={s.userId} className="rounded bg-gray-100 px-1.5 py-0.5">
                          {nameOf(s.userId)} {rupees(s.amountOwed)}
                        </span>
                      ))}
                    </p>
                  </div>
                  <button
                    className="shrink-0 text-sm text-red-600 hover:underline disabled:opacity-50"
                    onClick={() => remove(e)}
                    disabled={busy}
                  >
                    Delete
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      {showAdd && (
        <AddExpenseModal
          group={group}
          onClose={() => setShowAdd(false)}
          onAdded={() => {
            setShowAdd(false)
            page.reload()
          }}
        />
      )}
    </div>
  )
}
