import { Navigate } from 'react-router-dom'
import { useSupabaseAuth } from '../hooks/useSupabaseAuth'

/**
 * Wraps protected routes. Shows a spinner while auth is resolving,
 * redirects to /login if no session exists.
 */
export default function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { session, loading } = useSupabaseAuth()

  if (loading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center',
                    height: '100vh' }}>
        <div className="spinner" style={{ width: 28, height: 28 }} />
      </div>
    )
  }

  if (!session) {
    return <Navigate to="/login" replace />
  }

  return <>{children}</>
}
