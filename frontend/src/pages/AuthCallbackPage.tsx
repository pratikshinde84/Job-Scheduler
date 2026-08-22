import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { supabase } from '../lib/supabaseClient'

/**
 * Landing page after OAuth redirect.
 *
 * Strategy:
 * 1. Subscribe to onAuthStateChange immediately (before any async work) so
 *    the SIGNED_IN event is never missed regardless of timing.
 * 2. Then call getSession() — if a session is already parsed (fast path),
 *    navigate right away and cancel the subscription.
 * 3. If getSession() returns null, wait for SIGNED_IN from the subscription.
 * 4. A 10 s timeout guards against edge cases.
 *
 * The subscription and timeout refs allow the cleanup to always fire correctly
 * even in React StrictMode (double-invoke) or fast unmounts.
 */
export default function AuthCallbackPage() {
  const navigate = useNavigate()
  const navigatedRef = useRef(false)   // prevent double-navigation

  useEffect(() => {
    let timeoutId: ReturnType<typeof setTimeout>

    const goTo = (path: string) => {
      if (navigatedRef.current) return
      navigatedRef.current = true
      navigate(path, { replace: true })
    }

    // Step 1 — subscribe first so we never miss the event
    const { data: { subscription } } = supabase.auth.onAuthStateChange(
      (event, session) => {
        if (event === 'SIGNED_IN' && session) {
          clearTimeout(timeoutId)
          subscription.unsubscribe()
          goTo('/')
        } else if (event === 'SIGNED_OUT') {
          clearTimeout(timeoutId)
          subscription.unsubscribe()
          goTo('/login')
        }
        // Ignore INITIAL_SESSION — handled by getSession() below
      }
    )

    // Step 2 — fast path: session already available
    supabase.auth.getSession().then(({ data }) => {
      if (data.session) {
        clearTimeout(timeoutId)
        subscription.unsubscribe()
        goTo('/')
      }
    })

    // Step 3 — safety net: give up after 10 s
    timeoutId = setTimeout(() => {
      subscription.unsubscribe()
      goTo('/login')
    }, 10_000)

    return () => {
      // Cleanup on unmount (StrictMode double-invoke, fast navigation, etc.)
      clearTimeout(timeoutId)
      subscription.unsubscribe()
    }
  }, [navigate])

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center',
                  justifyContent: 'center', height: '100vh', gap: '16px' }}>
      <div className="spinner" style={{ width: 32, height: 32 }} />
      <p style={{ color: 'var(--text-muted)' }}>Completing sign-in…</p>
    </div>
  )
}
