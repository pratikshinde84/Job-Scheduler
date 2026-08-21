import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { supabase } from '../lib/supabaseClient'

/**
 * Landing page after OAuth redirect.
 * Supabase detectSessionInUrl handles parsing the fragment/query params;
 * we just wait for the session to be set then redirect.
 */
export default function AuthCallbackPage() {
  const navigate = useNavigate()

  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => {
      if (data.session) {
        navigate('/', { replace: true })
      } else {
        navigate('/login', { replace: true })
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
