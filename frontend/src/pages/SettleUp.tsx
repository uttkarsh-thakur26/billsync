import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { useActing } from '../acting'
import { errorMessage, getGroup, getSettlementPlan, recordSettlement } from '../api/client'
import type { TransactionResponse } from '../api/types'
import { rupees } from '../money'
import { Alert, btnSecondary, card, Empty, Loading } from '../ui'
import { useLoad } from '../useLoad'

export default function SettleUp() {
  const groupId = Number(useParams().groupId)
  const { nameOf } = useActing()
  const page = useLoad(() => Promise.all([getGroup(groupId), getSettlementPlan(groupId)]), [groupId])
  const [recording, setRecording] = useState<number | null>(null)
  const [error, setError] = useState<string>()

  if (!page.data) {
    return page.error ? <Alert message={page.error} /> : <Loading what="settlement plan" />
  }
  const [group, plan] = page.data

  async function record(t: TransactionResponse, index: number) {
    setRecording(index)
    setError(undefined)
    try {
      await recordSettlement(groupId, { fromUserId: t.fromUserId, toUserId: t.toUserId, amount: t.amount })
      page.reload()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setRecording(null)
    }
  }

  const count = plan.transactionCount
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-3">
        <Link to={`/groups/${groupId}`} className="text-sm text-gray-500 hover:underline">
          ← {group.name}
        </Link>
        <h1 className="text-2xl font-bold">Settle up</h1>
      </div>
      <Alert message={error ?? page.error} />

      {plan.transactions.length === 0 ? (
        <Empty>All settled up. Nobody owes anything.</Empty>
      ) : (
        <section className={card}>
          <p className="mb-3 text-sm text-gray-600">
            <span className="font-semibold text-gray-900">
              {count} payment{count === 1 ? '' : 's'}
            </span>
            {plan.naiveTransactionCount > count && ` instead of ${plan.naiveTransactionCount}`}
          </p>
          <ul className="divide-y divide-gray-100">
            {plan.transactions.map((t, i) => (
              <li key={`${t.fromUserId}-${t.toUserId}`} className="flex items-center gap-3 py-2 text-sm">
                <span className="flex-1">
                  <span className="font-medium">{nameOf(t.fromUserId)}</span> pays{' '}
                  <span className="font-medium">{nameOf(t.toUserId)}</span>{' '}
                  <span className="font-semibold">{rupees(t.amount)}</span>
                </span>
                <button className={btnSecondary} disabled={recording !== null} onClick={() => void record(t, i)}>
                  {recording === i ? 'Recording…' : 'Record'}
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}
      <p className="text-xs text-gray-500">Recording a payment saves a settlement and recomputes the plan.</p>
    </div>
  )
}
