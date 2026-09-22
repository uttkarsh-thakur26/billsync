import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useActing } from '../acting'
import { addExpense, errorMessage } from '../api/client'
import type { CreateExpenseRequest, GroupResponse, SplitType } from '../api/types'
import { fromHundredths, rupees, toHundredths } from '../money'
import { Alert, btn, btnSecondary, input } from '../ui'

const SPLIT_LABELS: Record<SplitType, string> = { EQUAL: 'Equally', EXACT: 'Exact amounts', PERCENTAGE: 'Percentages' }

interface Props {
  group: GroupResponse
  onClose: () => void
  onAdded: () => void
}

export default function AddExpenseModal({ group, onClose, onAdded }: Props) {
  const { actingUserId, nameOf } = useActing()
  const dialog = useRef<HTMLDialogElement>(null)
  const members = group.members

  const [description, setDescription] = useState('')
  const [amount, setAmount] = useState('')
  const [paidBy, setPaidBy] = useState(() =>
    actingUserId !== null && members.some((m) => m.id === actingUserId) ? actingUserId : members[0].id,
  )
  const [splitType, setSplitType] = useState<SplitType>('EQUAL')
  const [participants, setParticipants] = useState(() => new Set(members.map((m) => m.id)))
  const [values, setValues] = useState<Record<number, string>>({})
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string>()

  // A native <dialog> gives us the backdrop, focus trap and Escape-to-close for free.
  // No close() in cleanup: unmounting removes the element, and calling close() would
  // fire a 'close' event that unmounts us again via onClose (visible under StrictMode).
  useEffect(() => {
    dialog.current?.showModal()
  }, [])

  const amountPaise = toHundredths(amount)
  const chosen = members.filter((m) => participants.has(m.id))

  // Live running total for EXACT and PERCENTAGE, in hundredths: paise, or hundredths of a percent.
  const target = splitType === 'EXACT' ? amountPaise : splitType === 'PERCENTAGE' ? 10000 : null
  let entered = 0
  let allValid = true
  if (target !== null) {
    for (const m of chosen) {
      const raw = (values[m.id] ?? '').trim()
      const v = raw === '' ? 0 : toHundredths(raw) // an untouched field is simply zero
      if (v === null) allValid = false
      else entered += v
    }
  }
  const reconciled = target !== null && allValid && entered === target
  const canSubmit =
    description.trim() !== '' &&
    amountPaise !== null &&
    amountPaise > 0 &&
    chosen.length > 0 &&
    (splitType === 'EQUAL' || reconciled)

  function toggle(id: number) {
    setParticipants((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!canSubmit || amountPaise === null) return
    setSaving(true)
    setError(undefined)
    const body: CreateExpenseRequest = {
      paidByUserId: paidBy,
      amount: fromHundredths(amountPaise),
      description: description.trim(),
      splitType,
      participantUserIds: chosen.map((m) => m.id),
    }
    if (splitType !== 'EQUAL') {
      body.splitValues = Object.fromEntries(chosen.map((m) => [m.id, fromHundredths(toHundredths(values[m.id]) ?? 0)]))
    }
    try {
      await addExpense(group.id, body)
      onAdded()
    } catch (err) {
      setError(errorMessage(err))
      setSaving(false)
    }
  }

  function runningTotal() {
    if (target === null) return null
    const fmt = splitType === 'EXACT' ? (n: number) => rupees(fromHundredths(n)) : (n: number) => `${fromHundredths(n)}%`
    let status: string
    if (!allValid) status = 'some values are not valid numbers'
    else if (reconciled) status = 'adds up'
    else status = `${fmt(Math.abs(target - entered))} ${entered > target ? 'over' : 'to go'}`
    return (
      <p className={`mt-2 text-sm font-medium ${reconciled ? 'text-green-700' : 'text-red-600'}`} aria-live="polite">
        {fmt(entered)} of {fmt(target)} entered — {status}
      </p>
    )
  }

  return (
    <dialog
      ref={dialog}
      onClose={onClose}
      className="m-auto w-[calc(100%-2rem)] max-w-lg rounded-lg p-0 shadow-xl backdrop:bg-black/40"
    >
      <form onSubmit={submit} className="space-y-4 p-5">
        <h2 className="text-lg font-semibold">Add expense</h2>

        <label className="block text-sm">
          <span className="mb-1 block font-medium">Description</span>
          <input
            className={`${input} w-full`}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            required
            maxLength={255}
            autoFocus
          />
        </label>

        <div className="grid grid-cols-2 gap-3">
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Amount (₹)</span>
            <input
              className={`${input} w-full`}
              inputMode="decimal"
              placeholder="0.00"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              required
            />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Paid by</span>
            <select className={`${input} w-full`} value={paidBy} onChange={(e) => setPaidBy(Number(e.target.value))}>
              {members.map((m) => (
                <option key={m.id} value={m.id}>
                  {nameOf(m.id)}
                </option>
              ))}
            </select>
          </label>
        </div>

        <fieldset>
          <legend className="mb-1 text-sm font-medium">Split</legend>
          <div className="flex flex-wrap gap-4 text-sm">
            {(Object.keys(SPLIT_LABELS) as SplitType[]).map((t) => (
              <label key={t} className="flex items-center gap-1">
                <input type="radio" name="splitType" checked={splitType === t} onChange={() => setSplitType(t)} />
                {SPLIT_LABELS[t]}
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset>
          <legend className="mb-1 text-sm font-medium">Participants</legend>
          <ul className="space-y-1">
            {members.map((m) => (
              <li key={m.id} className="flex items-center gap-2 text-sm">
                <input
                  id={`participant-${m.id}`}
                  type="checkbox"
                  checked={participants.has(m.id)}
                  onChange={() => toggle(m.id)}
                />
                <label htmlFor={`participant-${m.id}`} className="flex-1">
                  {nameOf(m.id)}
                </label>
                {splitType !== 'EQUAL' && participants.has(m.id) && (
                  <input
                    className={`${input} w-28 text-right`}
                    inputMode="decimal"
                    placeholder={splitType === 'EXACT' ? '0.00' : '0'}
                    value={values[m.id] ?? ''}
                    onChange={(e) => setValues((v) => ({ ...v, [m.id]: e.target.value }))}
                    aria-label={`${splitType === 'EXACT' ? 'Amount' : 'Percentage'} for ${m.name}`}
                  />
                )}
              </li>
            ))}
          </ul>
          {chosen.length === 0 && <p className="mt-2 text-sm text-red-600">Pick at least one participant.</p>}
          {runningTotal()}
        </fieldset>

        <Alert message={error} />

        <div className="flex justify-end gap-2">
          <button type="button" className={btnSecondary} onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className={btn} disabled={!canSubmit || saving}>
            {saving ? 'Saving…' : 'Add expense'}
          </button>
        </div>
      </form>
    </dialog>
  )
}
