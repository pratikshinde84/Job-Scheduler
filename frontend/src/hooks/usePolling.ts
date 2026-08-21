import { useEffect, useRef } from 'react'

/**
 * Runs `fn` immediately and then on every `intervalMs`.
 * Pass `enabled=false` to pause polling (e.g. while the session is loading).
 * Cleans up the interval on unmount or when deps change.
 */
export function usePolling(
  fn: () => void,
  intervalMs: number,
  enabled = true
) {
  const fnRef = useRef(fn)
  fnRef.current = fn  // always call the latest closure

  useEffect(() => {
    if (!enabled) return

    fnRef.current()  // fire immediately
    const id = setInterval(() => fnRef.current(), intervalMs)
    return () => clearInterval(id)
  }, [intervalMs, enabled])
}
