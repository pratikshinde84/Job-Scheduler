import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { supabase } from '../lib/supabaseClient'

/**
 * Landing page after OAuth redirect.
 *
 * We MUST listen to onAuthStateChange here instead of calling getSession()
 * directly. When Supabase redirects back with a token in the URL hash, the
 * client needs a tick to parse the fragment and exchange it for a session.
 * Calling getSession() synchronously returns null during that window, which
 * causes an immediate redirect back to /login.
 *
 * onAuthStateChange fires SIGNED_IN once the session is actually ready,
 * giving us a reliable signal to navigate to the dashboard.
 */
export default function AuthCallbackPage() {
  const navigate = useNavigate()

  useEffect(() => {
    // First check: maybe the session is already available (e.g. page refresh
    // on /auth/callback after a successful sign-in).
    supabase.auth.getSession().then(({ data }) => {
      if (data.session) {
        navigate('/', { replace: true })
        return
      }

      // Session not ready yet — wait for the SIGNED_IN event which fires once
      // Supabase has finished parsing the token from the URL fragment.
      const { data: { subscription } } = supabase.auth.onAuthStateChange(
        (event, session) => {
          if (event === 'SIGNED_IN' && session) {
            subscription.unsubscribe()
            navigate('/', { replace: true })
          } else if (event === 'SIGNED_OUT' || (event !== 'INITIAL_SESSION' && !session)) {
            subscription.unsubscribe()
            navigate('/login', { replace: true })
          }
        }
      )

      // Safety timeout — if nothing fires in 5 s, give up and go to login
      const timeout = setTimeout(() => {
        subscription.unsubscribe()
        navigate('/login', { replace: true })
      }, 5000)

      return () => {
        subscription.unsubscribe()
        clearTimeout(timeout)
      }
    })
  }, [navigate])

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center',
                  justifyContent: 'center', height: '100vh', gap: '16px' }}>
      <div className="spinner" style={{ width: 32, height: 32 }} />
      <p style={{ color: 'var(--text-muted)' }}>Completing sign-in…</p>
    </div>
  )
}
