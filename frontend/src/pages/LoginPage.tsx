import { useSupabaseAuth } from '../hooks/useSupabaseAuth'
import { Navigate } from 'react-router-dom'

export default function LoginPage() {
  const { session, loading, signInWithGoogle, signInWithGitHub } = useSupabaseAuth()

  if (loading) return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh' }}>
      <div className="spinner" />
    </div>
  )

  if (session) return <Navigate to="/" replace />

  return (
    <div style={styles.root}>
      <div style={styles.card}>
        <div style={styles.logo}>
          <svg width="40" height="40" viewBox="0 0 40 40" fill="none">
            <rect width="40" height="40" rx="10" fill="#6366f1" />
            <path d="M12 28l4-8 4 4 4-8 4 8" stroke="#fff" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </div>
        <h1 style={styles.title}>Job Scheduler</h1>
        <p style={styles.subtitle}>Sign in to manage your distributed job queues</p>

        <div style={styles.buttons}>
          <button style={styles.oauthBtn} onClick={() => signInWithGoogle()}>
            <GoogleIcon />
            Continue with Google
          </button>
          <button style={styles.oauthBtn} onClick={() => signInWithGitHub()}>
            <GitHubIcon />
            Continue with GitHub
          </button>
        </div>

        <p style={styles.footer}>
          By signing in you agree to our terms of service.
        </p>
      </div>
    </div>
  )
}

function GoogleIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" style={{ marginRight: 8 }}>
      <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
      <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
      <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
      <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
    </svg>
  )
}

function GitHubIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" style={{ marginRight: 8 }}>
      <path d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z"/>
    </svg>
  )
}

const styles: Record<string, React.CSSProperties> = {
  root: {
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    background: 'var(--bg)',
    padding: '24px',
  },
  card: {
    background: 'var(--bg-card)',
    border: '1px solid var(--border)',
    borderRadius: '16px',
    padding: '48px 40px',
    width: '100%',
    maxWidth: '400px',
    textAlign: 'center',
    boxShadow: 'var(--shadow)',
  },
  logo: { marginBottom: '20px', display: 'flex', justifyContent: 'center' },
  title: { fontSize: '22px', fontWeight: 700, color: 'var(--text)', marginBottom: '8px' },
  subtitle: { color: 'var(--text-muted)', fontSize: '13px', marginBottom: '32px' },
  buttons: { display: 'flex', flexDirection: 'column', gap: '12px' },
  oauthBtn: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    background: 'var(--bg-hover)',
    color: 'var(--text)',
    border: '1px solid var(--border)',
    borderRadius: '8px',
    padding: '11px 16px',
    fontSize: '14px',
    fontWeight: 500,
    cursor: 'pointer',
    transition: 'background 0.15s',
  },
  footer: { marginTop: '28px', fontSize: '11px', color: 'var(--text-dim)' },
}
