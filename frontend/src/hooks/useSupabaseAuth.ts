import { useEffect, useState, useCallback } from 'react'
import type { Session, User } from '@supabase/supabase-js'
import { supabase } from '../lib/supabaseClient'
import type { AuthUser } from '../types'

interface AuthState {
  session: Session | null
  user: AuthUser | null
  loading: boolean
}

/** Map a Supabase User to our AuthUser shape. */
function toAuthUser(supaUser: User): AuthUser {
  const meta = supaUser.user_metadata ?? {}
  return {
    id: supaUser.id,
    email: supaUser.email ?? '',
    name: (meta.name ?? meta.full_name ?? supaUser.email?.split('@')[0] ?? '') as string,
    avatarUrl: (meta.avatar_url ?? meta.picture ?? null) as string | null,
  }
}

/**
 * Central auth hook.
 *
 * Why both getSession() AND onAuthStateChange?
 *
 * - getSession() gives us the current persisted session synchronously-ish
 *   and is the source of truth on every page load / refresh.
 * - onAuthStateChange catches subsequent sign-in / sign-out events that
 *   happen after the initial load.
 *
 * We call getSession() first to set the initial state, then subscribe for
 * changes. `loading` stays true until getSession() resolves so ProtectedRoute
 * never flickers to /login on a valid session.
 */
export function useSupabaseAuth() {
  const [state, setState] = useState<AuthState>({
    session: null,
    user: null,
    loading: true,   // stays true until getSession() resolves
  })

  useEffect(() => {
    let mounted = true

    // 1. Resolve persisted session — this is authoritative for initial load
    supabase.auth.getSession().then(({ data }) => {
      if (!mounted) return
      setState({
        session: data.session,
        user: data.session?.user ? toAuthUser(data.session.user) : null,
        loading: false,
      })
    })

    // 2. Subscribe for subsequent auth changes (sign-in, sign-out, token refresh)
    //    Skip INITIAL_SESSION here — getSession() above already handles it.
    const { data: { subscription } } = supabase.auth.onAuthStateChange(
      (event, session) => {
        if (event === 'INITIAL_SESSION') return  // handled by getSession()
        if (!mounted) return
        setState({
          session,
          user: session?.user ? toAuthUser(session.user) : null,
          loading: false,
        })
      }
    )

    return () => {
      mounted = false
      subscription.unsubscribe()
    }
  }, [])

  const signInWithGoogle = useCallback(() =>
    supabase.auth.signInWithOAuth({
      provider: 'google',
      options: { redirectTo: `${window.location.origin}/auth/callback` },
    }), [])

  const signInWithGitHub = useCallback(() =>
    supabase.auth.signInWithOAuth({
      provider: 'github',
      options: { redirectTo: `${window.location.origin}/auth/callback` },
    }), [])

  const signOut = useCallback(() => supabase.auth.signOut(), [])

  return { ...state, signInWithGoogle, signInWithGitHub, signOut }
}
