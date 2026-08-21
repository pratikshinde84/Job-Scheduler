import { useState, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { projectsApi } from '../api/projects'
import { usePolling } from '../hooks/usePolling'
import { useSessionReady } from '../hooks/useSessionReady'
import { toSlug } from '../lib/slug'
import type { Project } from '../types'

export default function ProjectsPage() {
  const [projects, setProjects] = useState<Project[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)
  const [newName, setNewName] = useState('')
  const [saving, setSaving] = useState(false)

  const load = useCallback(async () => {
    try {
      setProjects(await projectsApi.list())
      setError(null)
    } catch {
      setError('Failed to load projects.')
    } finally {
      setLoading(false)
    }
  }, [])

  const sessionReady = useSessionReady()
  usePolling(load, 15000, sessionReady)

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newName.trim()) return
    setSaving(true)
    try {
      const p = await projectsApi.create(newName.trim())
      setProjects(prev => [p, ...prev])
      setNewName('')
      setCreating(false)
    } catch {
      alert('Failed to create project.')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (id: string, name: string) => {
    if (!confirm(`Delete project "${name}"? All queues and jobs will be removed.`)) return
    try {
      await projectsApi.delete(id)
      setProjects(prev => prev.filter(p => p.id !== id))
    } catch {
      alert('Failed to delete project.')
    }
  }

  if (loading) return <div style={{ padding: 40, textAlign: 'center' }}><div className="spinner" /></div>

  return (
    <div style={styles.page}>
      <header style={styles.header}>
        <h1 style={styles.pageTitle}>Projects</h1>
        <button className="btn-primary" onClick={() => setCreating(true)}>+ New Project</button>
      </header>

      {error && <div className="error-banner" style={{ marginBottom: 16 }}>{error}</div>}

      {creating && (
        <form className="card" onSubmit={handleCreate}
          style={{ marginBottom: 20, display: 'flex', gap: 10, alignItems: 'center' }}>
          <input
            autoFocus
            placeholder="Project name  e.g. My App"
            value={newName}
            onChange={e => setNewName(e.target.value)}
            style={{ flex: 1 }}
          />
          {newName.trim() && (
            <span style={styles.slugPreview}>/{toSlug(newName.trim())}</span>
          )}
          <button className="btn-primary" type="submit" disabled={saving}>
            {saving ? 'Creating…' : 'Create'}
          </button>
          <button className="btn-ghost" type="button" onClick={() => setCreating(false)}>Cancel</button>
        </form>
      )}

      {projects.length === 0 ? (
        <div className="empty-state">
          <p>No projects yet. Create one to get started.</p>
        </div>
      ) : (
        <div style={styles.grid}>
          {projects.map(p => (
            <div key={p.id} className="card" style={styles.projectCard}>
              <div style={styles.cardTop}>
                {/* Link uses the slug instead of UUID */}
                <Link to={`/projects/${toSlug(p.name)}`} style={styles.projectName}>
                  {p.name}
                </Link>
                <button className="btn-danger" style={{ fontSize: 11, padding: '4px 8px' }}
                  onClick={() => handleDelete(p.id, p.name)}>Delete</button>
              </div>
              <div style={styles.meta}>
                <span style={styles.pill}>{p.queueCount} queue{p.queueCount !== 1 ? 's' : ''}</span>
                {/* Show slug so user knows what the URL will be */}
                <span style={styles.slugTag}>/projects/{toSlug(p.name)}</span>
              </div>
              <div style={styles.created}>
                Created {new Date(p.createdAt).toLocaleDateString()}
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
  header: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 28 },
  pageTitle: { fontSize: '22px', fontWeight: 700 },
  grid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 16 },
  projectCard: { display: 'flex', flexDirection: 'column', gap: 10 },
  cardTop: { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' },
  projectName: { fontSize: '15px', fontWeight: 600, color: 'var(--text)' },
  meta: { display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' as const },
  pill: { background: 'var(--bg-hover)', borderRadius: 12, padding: '2px 8px',
          fontSize: '11px', color: 'var(--text-muted)' },
  slugTag: { fontFamily: 'var(--mono)', fontSize: '11px', color: 'var(--accent)',
             opacity: 0.7 },
  slugPreview: { fontFamily: 'var(--mono)', fontSize: '11px', color: 'var(--accent)',
                 background: 'rgba(99,102,241,0.1)', padding: '3px 8px', borderRadius: 4 },
  created: { fontSize: '11px', color: 'var(--text-dim)' },
}
