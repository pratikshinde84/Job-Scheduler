import { useState, useCallback } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { projectsApi } from '../api/projects'
import { queuesApi } from '../api/queues'
import { usePolling } from '../hooks/usePolling'
import { useSessionReady } from '../hooks/useSessionReady'
import { findBySlug, toSlug } from '../lib/slug'
import type { Project, Queue } from '../types'

export default function QueuesPage() {
  // :projectName is the slug, e.g. "my-app"
  const { projectName } = useParams<{ projectName: string }>()
  const navigate = useNavigate()

  const [project, setProject] = useState<Project | null>(null)
  const [queues, setQueues] = useState<Queue[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)
  const [form, setForm] = useState({ name: '', concurrencyLimit: 5 })
  const [saving, setSaving] = useState(false)

  const load = useCallback(async () => {
    if (!projectName) return
    try {
      // Load all projects, find the one whose slug matches the URL param
      const all = await projectsApi.list()
      const matched = findBySlug(all, projectName)
      if (!matched) {
        setError(`Project "${projectName}" not found.`)
        setLoading(false)
        return
      }
      setProject(matched)
      setQueues(await queuesApi.list(matched.id))
      setError(null)
    } catch {
      setError('Failed to load queues.')
    } finally {
      setLoading(false)
    }
  }, [projectName])

  const sessionReady = useSessionReady()
  usePolling(load, 10000, sessionReady)

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!project || !form.name.trim()) return
    setSaving(true)
    try {
      const q = await queuesApi.create(project.id, {
        name: form.name.trim(),
        concurrencyLimit: form.concurrencyLimit,
      })
      setQueues(prev => [...prev, q])
      setForm({ name: '', concurrencyLimit: 5 })
      setCreating(false)
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { detail?: string } } })
        ?.response?.data?.detail
      alert(msg ?? 'Failed to create queue.')
    } finally {
      setSaving(false)
    }
  }

  const togglePause = async (q: Queue) => {
    if (!project) return
    try {
      if (q.isPaused) {
        await queuesApi.resume(project.id, q.id)
      } else {
        await queuesApi.pause(project.id, q.id)
      }
      setQueues(prev => prev.map(x => x.id === q.id ? { ...x, isPaused: !q.isPaused } : x))
    } catch {
      alert('Failed to update queue.')
    }
  }

  const handleDelete = async (q: Queue) => {
    if (!project) return
    if (!confirm(`Delete queue "${q.name}"?`)) return
    try {
      await queuesApi.delete(project.id, q.id)
      setQueues(prev => prev.filter(x => x.id !== q.id))
    } catch {
      alert('Failed to delete queue.')
    }
  }

  if (loading) return <div style={{ padding: 40, textAlign: 'center' }}><div className="spinner" /></div>

  if (error) return (
    <div style={{ maxWidth: 600, margin: '60px auto', padding: 24 }}>
      <div className="error-banner">{error}</div>
      <button className="btn-ghost" style={{ marginTop: 16 }}
        onClick={() => navigate('/projects')}>← Back to Projects</button>
    </div>
  )

  return (
    <div style={styles.page}>
      <header style={styles.header}>
        <div>
          <nav style={styles.breadcrumb}>
            <Link to="/projects" style={styles.crumbLink}>Projects</Link>
            <span style={styles.crumbSep}>/</span>
            <span style={{ color: 'var(--text)', fontWeight: 500 }}>
              {project?.name ?? projectName}
            </span>
          </nav>
          <h1 style={styles.pageTitle}>Queues</h1>
        </div>
        <button className="btn-primary" onClick={() => setCreating(true)}>+ New Queue</button>
      </header>

      {creating && (
        <form className="card" onSubmit={handleCreate} style={{ marginBottom: 20 }}>
          <div style={styles.formRow}>
            <label style={styles.label}>Queue Name</label>
            <div style={{ flex: 1, display: 'flex', gap: 8, alignItems: 'center' }}>
              <input
                autoFocus required
                placeholder="e.g. email-notifications"
                value={form.name}
                onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
                style={{ flex: 1 }}
              />
              {form.name.trim() && (
                <span style={styles.slugPreview}>
                  /queues/{toSlug(form.name.trim())}/jobs
                </span>
              )}
            </div>
          </div>
          <div style={styles.formRow}>
            <label style={styles.label}>Concurrency</label>
            <input
              type="number" min={1} max={100}
              value={form.concurrencyLimit}
              onChange={e => setForm(f => ({ ...f, concurrencyLimit: +e.target.value }))}
              style={{ width: 80 }}
            />
          </div>
          <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <button className="btn-ghost" type="button" onClick={() => setCreating(false)}>Cancel</button>
            <button className="btn-primary" type="submit" disabled={saving}>
              {saving ? 'Creating…' : 'Create Queue'}
            </button>
          </div>
        </form>
      )}

      {queues.length === 0 ? (
        <div className="empty-state">
          <p>No queues yet. Create one to start processing jobs.</p>
        </div>
      ) : (
        <div style={styles.list}>
          {queues.map(q => (
            <div key={q.id} className="card" style={styles.queueRow}>
              <div style={styles.queueLeft}>
                {/* Human-readable URL: /projects/my-app/queues/email-notifications/jobs */}
                <Link
                  to={`/projects/${projectName}/queues/${toSlug(q.name)}/jobs`}
                  style={styles.queueName}
                >
                  {q.name}
                </Link>
                <div style={styles.queueMeta}>
                  <span style={styles.metaTag}>concurrency: {q.concurrencyLimit}</span>
                  {q.defaultRetryConfig && (
                    <span style={styles.metaTag}>
                      retry: {q.defaultRetryConfig.strategy} ×{q.defaultRetryConfig.max_attempts}
                    </span>
                  )}
                  <span style={styles.urlTag}>
                    /projects/{projectName}/queues/{toSlug(q.name)}/jobs
                  </span>
                </div>
              </div>
              <div style={styles.queueRight}>
                <span className={`badge ${q.isPaused ? 'badge-dead' : 'badge-running'}`}>
                  {q.isPaused ? 'paused' : 'active'}
                </span>
                <button className="btn-ghost" style={{ fontSize: 12 }}
                  onClick={() => togglePause(q)}>
                  {q.isPaused ? 'Resume' : 'Pause'}
                </button>
                <button className="btn-danger" style={{ fontSize: 12 }}
                  onClick={() => handleDelete(q)}>Delete</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  page: { maxWidth: 1000, margin: '0 auto', padding: '32px 24px' },
  header: { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 28 },
  breadcrumb: { display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 },
  crumbLink: { color: 'var(--text-muted)', fontSize: 12 },
  crumbSep: { color: 'var(--text-dim)', fontSize: 12 },
  pageTitle: { fontSize: '22px', fontWeight: 700 },
  formRow: { display: 'flex', alignItems: 'center', gap: 12, marginBottom: 14 },
  label: { width: 100, fontSize: 13, color: 'var(--text-muted)', flexShrink: 0 },
  slugPreview: { fontFamily: 'var(--mono)', fontSize: '11px', color: 'var(--accent)',
                 background: 'rgba(99,102,241,0.1)', padding: '3px 8px', borderRadius: 4,
                 whiteSpace: 'nowrap' as const },
  list: { display: 'flex', flexDirection: 'column', gap: 10 },
  queueRow: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' },
  queueLeft: { display: 'flex', flexDirection: 'column', gap: 6 },
  queueName: { fontSize: '15px', fontWeight: 600, color: 'var(--text)' },
  queueMeta: { display: 'flex', gap: 8, flexWrap: 'wrap' as const, alignItems: 'center' },
  metaTag: { background: 'var(--bg-hover)', borderRadius: 10, padding: '2px 8px',
             fontSize: 11, color: 'var(--text-muted)' },
  urlTag: { fontFamily: 'var(--mono)', fontSize: 10, color: 'var(--accent)', opacity: 0.6 },
  queueRight: { display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 },
}
