import { useEffect, useState, useCallback } from 'react'
import type { Session, User } from '@supabase/supabase-js'
import { supabase } from '../lib/supabaseClient'
import type { AuthUser } from '../types'

interface AuthState {
  session: Session | null
  user: AuthUser | null
  loading: boolean
}

/**
 * Central auth hook. Wraps Supabase session management and exposes:
 *  - session / user  : current session state
 *  - loading         : true while initial session is being resolved
 *  - signInWithGoogle / signInWithGitHub : trigger OAuth flow
 *  - signOut         : clears the session
 */
export function useSupabaseAuth() {
  const [state, setState] = useState<AuthState>({
    session: null,
    user: null,
    loading: true,
  })

  // Map Supabase User → our AuthUser shape
  const toAuthUser = (supaUser: User): AuthUser => {
    const meta = supaUser.user_metadata ?? {}
    return {
      id: supaUser.id,
      email: supaUser.email ?? '',
      name: (meta.name ?? meta.full_name ?? supaUser.email?.split('@')[0] ?? '') as string,
      avatarUrl: (meta.avatar_url ?? meta.picture ?? null) as string | null,
    }
  }

  useEffect(() => {
    // 1. Resolve any existing session (handles page refresh + OAuth callback)
    supabase.auth.getSession().then(({ data }) => {
      setState({
        session: data.session,
        user: data.session?.user ? toAuthUser(data.session.user) : null,
        loading: false,
      })
    })

    // 2. Subscribe to subsequent auth state changes
    const { data: { subscription } } = supabase.auth.onAuthStateChange(
      (_event, session) => {
        setState({
          session,
          user: session?.user ? toAuthUser(session.user) : null,
          loading: false,
        })
      }
    )

    return () => subscription.unsubscribe()
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
