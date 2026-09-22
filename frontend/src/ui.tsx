import type { ReactNode } from 'react'

export const btn =
  'rounded-md bg-gray-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-gray-700 disabled:cursor-not-allowed disabled:opacity-50'
export const btnSecondary =
  'rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50'
export const input =
  'rounded-md border border-gray-300 bg-white px-2 py-1.5 text-sm text-gray-900 focus:border-gray-500 focus:outline-none'
export const card = 'rounded-lg border border-gray-200 bg-white p-4'
export const heading = 'mb-2 text-xs font-semibold uppercase tracking-wide text-gray-500'

export function Alert({ message }: { message?: string }) {
  if (!message) return null
  return (
    <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
      {message}
    </p>
  )
}

export function Loading({ what }: { what: string }) {
  return <p className="text-sm text-gray-500">Loading {what}…</p>
}

export function Empty({ children }: { children: ReactNode }) {
  return (
    <p className="rounded-lg border border-dashed border-gray-300 p-6 text-center text-sm text-gray-500">{children}</p>
  )
}
