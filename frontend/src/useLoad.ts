import { useEffect, useEffectEvent, useState } from 'react'
import { errorMessage } from './api/client'

interface LoadState<T> {
  key: string
  version: number
  data?: T
  error?: string
  loading: boolean
}

/**
 * Runs `load` on mount and whenever `deps` change; `reload()` runs it again in
 * place, keeping the previous data on screen until the new data arrives.
 */
export function useLoad<T>(load: () => Promise<T>, deps: readonly unknown[]) {
  const key = JSON.stringify(deps)
  const [state, setState] = useState<LoadState<T>>({ key, version: 0, loading: true })

  // Deps changed since the last render: forget the old result before the effect refetches.
  if (state.key !== key) setState({ key, version: 0, loading: true })

  // Always calls the latest `load` without making it an effect dependency.
  const run = useEffectEvent(() => load())

  useEffect(() => {
    let cancelled = false
    run().then(
      (data) => {
        if (!cancelled) setState((s) => ({ ...s, data, error: undefined, loading: false }))
      },
      (e: unknown) => {
        if (!cancelled) setState((s) => ({ ...s, error: errorMessage(e), loading: false }))
      },
    )
    return () => {
      cancelled = true
    }
  }, [key, state.version])

  return {
    data: state.data,
    error: state.error,
    loading: state.loading,
    reload: () => setState((s) => ({ ...s, version: s.version + 1, loading: true })),
  }
}
