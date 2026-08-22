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
    // Subscribe FIRST so we never miss an auth event that fires before
    // getSession() resolves. onAuthStateChange fires INITIAL_SESSION with
    // the current session (or null) shortly after mount, which is the
    // authoritative signal to set loading=false.
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
