import { useState, useCallback } from 'react'
import { dashboardApi } from '../api/dashboard'
import { usePolling } from '../hooks/usePolling'
import { useSessionReady } from '../hooks/useSessionReady'
import type { DashboardData, JobStatus, WorkerStatus } from '../types'
import TerminalLogViewer from '../components/TerminalLogViewer'

const STATUS_ORDER: JobStatus[] = ['running', 'pending', 'scheduled', 'claimed', 'completed', 'failed', 'dead']

const STATUS_LABELS: Record<JobStatus, string> = {
  running: 'Running', pending: 'Pending', scheduled: 'Scheduled',
  claimed: 'Claimed', completed: 'Completed', failed: 'Failed', dead: 'Dead',
}

export default function DashboardPage() {
  const [data, setData] = useState<DashboardData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [lastRefreshed, setLastRefreshed] = useState<Date | null>(null)

  const fetch = useCallback(async () => {
    try {
      const d = await dashboardApi.get()
      setData(d)
      setLastRefreshed(new Date())
      setError(null)
    } catch {
      setError('Failed to load dashboard data.')
    }
  }, [])

  const sessionReady = useSessionReady()
  usePolling(fetch, 5000, sessionReady)

  if (!data && !error) {
    return <div style={{ padding: '40px', textAlign: 'center' }}><div className="spinner" /></div>
  }

  return (
    <div style={styles.page}>
      <header style={styles.header}>
        <div>
          <h1 style={styles.pageTitle}>Dashboard</h1>
          {lastRefreshed && (
            <p style={styles.refreshed}>Last updated {lastRefreshed.toLocaleTimeString()}</p>
          )}
        </div>
      </header>

      {error && <div className="error-banner" style={{ marginBottom: 24 }}>{error}</div>}

      {data && (
        <>
          {/* ── Stat Cards ───────────────────────────────────────── */}
          <div style={styles.statsGrid}>
            <StatCard label="Total Jobs" value={data.totalJobs} color="var(--accent)" />
            <StatCard label="Projects" value={data.totalProjects} color="var(--success)" />
            <StatCard label="Queues" value={data.totalQueues} color="var(--info)" />
            <StatCard label="Active Workers"
              value={data.workers.filter(w => w.status === 'active').length}
              color="var(--warning)" />
          </div>

          {/* ── Job Status Breakdown ──────────────────────────────── */}
          <section className="card" style={{ marginBottom: 24 }}>
            <h2 style={styles.sectionTitle}>Job Status Breakdown</h2>
            <div style={styles.statusGrid}>
              {STATUS_ORDER.map(status => {
                const count = data.jobCountsByStatus[status] ?? 0
                return (
                  <div key={status} style={styles.statusCell}>
                    <span className={`badge badge-${status}`}>{STATUS_LABELS[status]}</span>
                    <span style={styles.statusCount}>{count.toLocaleString()}</span>
                  </div>
                )
              })}
            </div>
            {data.totalJobs > 0 && (
              <ProgressBar counts={data.jobCountsByStatus} total={data.totalJobs} />
            )}
          </section>

          {/* ── Workers ──────────────────────────────────────────── */}
          <section className="card">
            <h2 style={styles.sectionTitle}>Workers</h2>
            {data.workers.length === 0 ? (
              <p className="empty-state">No workers registered yet.</p>
            ) : (
              <table style={styles.table}>
                <thead>
                  <tr>
                    {['Name', 'Status', 'Last Heartbeat'].map(h => (
                      <th key={h} style={styles.th}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {data.workers.map(w => (
                    <tr key={w.name} style={styles.tr}>
                      <td style={{ ...styles.td, fontFamily: 'var(--mono)' }}>{w.name}</td>
                      <td style={styles.td}>
                        <span className={`badge badge-${w.status as WorkerStatus}`}>{w.status}</span>
                      </td>
                      <td style={{ ...styles.td, color: 'var(--text-muted)' }}>
                        {w.lastHeartbeatAt
                          ? new Date(w.lastHeartbeatAt).toLocaleString()
                          : '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>

          {/* ── Terminal Execution Log Screen ──────────────────────── */}
          <TerminalLogViewer />
        </>
      )}
    </div>
  )
}


function StatCard({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div className="card" style={{ textAlign: 'center' }}>
      <div style={{ fontSize: '32px', fontWeight: 700, color }}>{value.toLocaleString()}</div>
      <div style={{ color: 'var(--text-muted)', fontSize: '12px', marginTop: 4 }}>{label}</div>
    </div>
  )
}

function ProgressBar({ counts, total }: {
  counts: Partial<Record<JobStatus, number>>
  total: number
}) {
  const segments: { status: JobStatus; color: string }[] = [
    { status: 'running', color: 'var(--success)' },
    { status: 'pending', color: 'var(--accent)' },
    { status: 'scheduled', color: '#a5b4fc' },
    { status: 'claimed', color: 'var(--text-muted)' },
    { status: 'completed', color: '#166534' },
    { status: 'failed', color: 'var(--danger)' },
    { status: 'dead', color: '#7f1d1d' },
  ]
  return (
    <div style={{ display: 'flex', height: 8, borderRadius: 4, overflow: 'hidden',
                  background: 'var(--border)', marginTop: 20 }}>
      {segments.map(({ status, color }) => {
        const pct = ((counts[status] ?? 0) / total) * 100
        if (pct === 0) return null
        return <div key={status} style={{ width: `${pct}%`, background: color }} title={`${status}: ${counts[status]}`} />
      })}
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  page: { maxWidth: 1000, margin: '0 auto', padding: '32px 24px' },
  header: { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 28 },
  pageTitle: { fontSize: '22px', fontWeight: 700 },
  refreshed: { fontSize: '11px', color: 'var(--text-dim)', marginTop: 4 },
  statsGrid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 16, marginBottom: 24 },
  sectionTitle: { fontSize: '15px', fontWeight: 600, marginBottom: 16 },
  statusGrid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(130px, 1fr))', gap: 12 },
  statusCell: { display: 'flex', flexDirection: 'column', gap: 6 },
  statusCount: { fontSize: '22px', fontWeight: 700 },
  table: { width: '100%', borderCollapse: 'collapse' },
  th: { textAlign: 'left', padding: '8px 12px', color: 'var(--text-muted)', fontSize: '11px',
        fontWeight: 600, textTransform: 'uppercase', borderBottom: '1px solid var(--border)' },
  td: { padding: '10px 12px', borderBottom: '1px solid var(--border)', fontSize: '13px' },
  tr: {},
}
