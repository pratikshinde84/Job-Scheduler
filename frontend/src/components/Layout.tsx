import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useSupabaseAuth } from '../hooks/useSupabaseAuth'

const NAV_ITEMS = [
  { to: '/',         label: 'Dashboard', icon: '▦' },
  { to: '/projects', label: 'Projects',  icon: '◫' },
  { to: '/jobs',     label: 'All Jobs',  icon: '≡' },
]

export default function Layout() {
  const { user, signOut } = useSupabaseAuth()
  const navigate = useNavigate()

  const handleSignOut = async () => {
    await signOut()
    navigate('/login', { replace: true })
  }

  return (
    <div style={styles.shell}>
      {/* ── Sidebar ─────────────────────────────────────────── */}
      <aside style={styles.sidebar}>
        {/* Logo */}
        <div style={styles.brand}>
          <svg width="28" height="28" viewBox="0 0 40 40" fill="none" style={{ flexShrink: 0 }}>
            <rect width="40" height="40" rx="9" fill="#6366f1" />
            <path d="M12 28l4-8 4 4 4-8 4 8" stroke="#fff" strokeWidth="2.5"
              strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          <span style={styles.brandName}>JobScheduler</span>
        </div>

        {/* Nav */}
        <nav style={styles.nav}>
          {NAV_ITEMS.map(item => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              style={({ isActive }) => ({
                ...styles.navLink,
                ...(isActive ? styles.navLinkActive : {}),
              })}
            >
              <span style={styles.navIcon}>{item.icon}</span>
              {item.label}
            </NavLink>
          ))}
        </nav>

        {/* User info at bottom */}
        <div style={styles.userSection}>
          {user?.avatarUrl && (
            <img src={user.avatarUrl} alt={user.name}
              style={styles.avatar} referrerPolicy="no-referrer" />
          )}
          <div style={styles.userInfo}>
            <div style={styles.userName}>{user?.name || 'User'}</div>
            <div style={styles.userEmail}>{user?.email}</div>
          </div>
          <button
            onClick={handleSignOut}
            style={styles.signOutBtn}
            title="Sign out"
            aria-label="Sign out"
          >
            ⏻
          </button>
        </div>
      </aside>

      {/* ── Main content ─────────────────────────────────────── */}
      <main style={styles.main}>
        <Outlet />
      </main>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  shell: {
    display: 'flex',
    height: '100vh',
    overflow: 'hidden',
  },
  sidebar: {
    width: 220,
    flexShrink: 0,
    background: 'var(--bg-card)',
    borderRight: '1px solid var(--border)',
    display: 'flex',
    flexDirection: 'column',
    padding: '16px 12px',
    gap: 0,
  },
  brand: {
    display: 'flex',
    alignItems: 'center',
    gap: 10,
    padding: '8px 8px 20px',
  },
  brandName: {
    fontSize: 14,
    fontWeight: 700,
    color: 'var(--text)',
    letterSpacing: '-0.02em',
  },
  nav: {
    display: 'flex',
    flexDirection: 'column',
    gap: 2,
    flex: 1,
  },
  navLink: {
    display: 'flex',
    alignItems: 'center',
    gap: 10,
    padding: '9px 10px',
    borderRadius: 7,
    color: 'var(--text-muted)',
    fontSize: 13,
    fontWeight: 500,
    textDecoration: 'none',
    transition: 'background 0.12s, color 0.12s',
  },
  navLinkActive: {
    background: 'rgba(99,102,241,0.15)',
    color: 'var(--accent-hover)',
  },
  navIcon: {
    fontSize: 15,
    width: 18,
    textAlign: 'center' as const,
    flexShrink: 0,
  },
  userSection: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    padding: '12px 8px',
    borderTop: '1px solid var(--border)',
    marginTop: 8,
  },
  avatar: {
    width: 30,
    height: 30,
    borderRadius: '50%',
    objectFit: 'cover' as const,
    flexShrink: 0,
  },
  userInfo: {
    flex: 1,
    minWidth: 0,
  },
  userName: {
    fontSize: 12,
    fontWeight: 600,
    color: 'var(--text)',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap' as const,
  },
  userEmail: {
    fontSize: 11,
    color: 'var(--text-dim)',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap' as const,
  },
  signOutBtn: {
    background: 'transparent',
    color: 'var(--text-muted)',
    border: 'none',
    fontSize: 16,
    padding: '4px 6px',
    borderRadius: 6,
    cursor: 'pointer',
    flexShrink: 0,
  },
  main: {
    flex: 1,
    overflow: 'auto',
    background: 'var(--bg)',
  },
}
