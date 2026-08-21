import { useState, useEffect } from 'react'
import { supabase } from '../lib/supabaseClient'

/**
 * Returns true once we've confirmed a valid Supabase session exists.
 * Prevents API calls from firing before the token is available.
 */
export function useSessionReady(): boolean {
  const [ready, setReady] = useState(false)

  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => {
      setReady(!!data.session)
    })

    const { data: { subscription } } = supabase.auth.onAuthStateChange((_e, session) => {
      setReady(!!session)
    })

    return () => subscription.unsubscribe()
  }, [])

  return ready
}
