import { useState, useCallback } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { projectsApi } from '../api/projects'
import { queuesApi } from '../api/queues'
import { jobsApi } from '../api/jobs'
import { usePolling } from '../hooks/usePolling'
import { useSessionReady } from '../hooks/useSessionReady'
import { findBySlug } from '../lib/slug'
import EnqueueJobModal from '../components/EnqueueJobModal'
import type { Job, JobStatus, Page, Project, Queue } from '../types'

const STATUSES: JobStatus[] = ['pending', 'scheduled', 'claimed', 'running', 'completed', 'failed', 'dead']
const PAGE_SIZE = 20

export default function JobsPage() {
  const { projectName, queueName } = useParams<{ projectName?: string; queueName?: string }>()
  const navigate = useNavigate()

  const [project, setProject]       = useState<Project | null>(null)
  const [queue, setQueue]           = useState<Queue | null>(null)
  const [page, setPage]             = useState<Page<Job> | null>(null)
  const [currentPage, setCurrentPage] = useState(0)
  const [statusFilter, setStatusFilter] = useState<JobStatus | ''>('')
  const [loading, setLoading]       = useState(true)
  const [error, setError]           = useState<string | null>(null)
  const [showEnqueueModal, setShowEnqueueModal] = useState(false)

  const load = useCallback(async () => {
    try {
      if (projectName && queueName) {
        const allProjects = await projectsApi.list()
        const matchedProject = findBySlug(allProjects, projectName)
        if (!matchedProject) {
          setError(`Project "${projectName}" not found.`)
          setLoading(false)
          return
        }
        setProject(matchedProject)

        const allQueues = await queuesApi.list(matchedProject.id)
        const matchedQueue = findBySlug(allQueues, queueName)
        if (!matchedQueue) {
          setError(`Queue "${queueName}" not found in project "${matchedProject.name}".`)
          setLoading(false)
          return
        }
        setQueue(matchedQueue)

        const data = await jobsApi.listByQueue(matchedQueue.id, {
          status: statusFilter || undefined,
          page: currentPage,
          size: PAGE_SIZE,
        })
        setPage(data)
      } else {
        const data = await jobsApi.listMine({ page: currentPage, size: PAGE_SIZE })
        setPage(data)
      }
      setError(null)
    } catch {
      setError('Failed to load jobs.')
    } finally {
      setLoading(false)
    }
  }, [projectName, queueName, statusFilter, currentPage])

  const sessionReady = useSessionReady()
  usePolling(load, 4000, sessionReady)

  const handleCancel = async (jobId: string) => {
    try {
      await jobsApi.cancel(jobId)
      load()
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail
      alert(msg ?? 'Cannot cancel this job.')
    }
  }

  const handleRequeue = async (jobId: string) => {
    try {
      await jobsApi.requeue(jobId)
      load()
    } catch {
      alert('Failed to requeue job.')
    }
  }

  const isQueueView = Boolean(projectName && queueName)

  const breadcrumb = isQueueView ? (
    <nav style={styles.breadcrumb}>
      <Link to="/projects" style={styles.crumbLink}>Projects</Link>
      <span style={styles.crumbSep}>/</span>
      <Link to={`/projects/${projectName}`} style={styles.crumbLink}>
        {project?.name ?? projectName}
      </Link>
      <span style={styles.crumbSep}>/</span>
      <span style={{ color: 'var(--text)', fontWeight: 500 }}>
        {queue?.name ?? queueName}
      </span>
    </nav>
  ) : (
    <nav style={styles.breadcrumb}>
      <span style={{ color: 'var(--text)', fontWeight: 500 }}>All Jobs</span>
    </nav>
  )

  if (error) return (
    <div style={{ maxWidth: 600, margin: '60px auto', padding: 24 }}>
      <div className="error-banner">{error}</div>
      <button className="btn-ghost" style={{ marginTop: 16 }}
        onClick={() => navigate(projectName ? `/projects/${projectName}` : '/projects')}>
        ← Go Back
      </button>
    </div>
  )

  return (
    <>
    <div style={styles.page}>
      <header style={styles.header}>
        <div>
          {breadcrumb}
          <h1 style={styles.pageTitle}>
            {isQueueView ? (queue?.name ?? queueName) : 'All Jobs'}
          </h1>
          {isQueueView && queue && (
            <div style={styles.queueMeta}>
              <span style={styles.metaTag}>concurrency: {queue.concurrencyLimit}</span>
              <span className={`badge ${queue.isPaused ? 'badge-dead' : 'badge-running'}`}
                style={{ fontSize: 10 }}>
                {queue.isPaused ? 'paused' : 'active'}
              </span>
            </div>
          )}
        </div>
        {isQueueView && (
          <button className="btn-primary" onClick={() => setShowEnqueueModal(true)}>
            + Enqueue Job
          </button>
        )}
      </header>

      {/* ── Status filter ────────────────────────────────────────────────── */}
      <div style={styles.filterRow}>
        <button className={statusFilter === '' ? 'btn-primary' : 'btn-ghost'} style={{ fontSize: 12 }}
          onClick={() => { setStatusFilter(''); setCurrentPage(0) }}>All</button>
        {STATUSES.map(s => (
          <button key={s}
            className={statusFilter === s ? 'btn-primary' : 'btn-ghost'}
            style={{ fontSize: 12 }}
            onClick={() => { setStatusFilter(s); setCurrentPage(0) }}>
            {s}
          </button>
        ))}
      </div>

      {loading && !page ? (
        <div style={{ textAlign: 'center', padding: 40 }}><div className="spinner" /></div>
      ) : page?.content.length === 0 ? (
        <div className="empty-state"><p>No jobs match this filter.</p></div>
      ) : (
        <>
          <div style={{ overflowX: 'auto' }}>
            <table style={styles.table}>
              <thead>
                <tr>
                  {['Job ID', 'Status', 'Priority', 'Attempts', 'Scheduled At', 'Actions'].map(h => (
                    <th key={h} style={styles.th}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {page?.content.map(job => (
                  <JobRow key={job.id} job={job}
                    onCancel={handleCancel} onRequeue={handleRequeue} />
                ))}
              </tbody>
            </table>
          </div>

          {page && page.totalPages > 1 && (
            <div style={styles.pagination}>
              <button className="btn-ghost" disabled={currentPage === 0}
                onClick={() => setCurrentPage(p => p - 1)}>← Prev</button>
              <span style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                Page {currentPage + 1} of {page.totalPages} ({page.totalElements.toLocaleString()} total)
              </span>
              <button className="btn-ghost" disabled={currentPage >= page.totalPages - 1}
                onClick={() => setCurrentPage(p => p + 1)}>Next →</button>
            </div>
          )}
        </>
      )}
    </div>

    {/* Enqueue modal */}
    {showEnqueueModal && queue && (
      <EnqueueJobModal
        lockedQueue={queue}
        onClose={() => setShowEnqueueModal(false)}
        onSuccess={() => { setShowEnqueueModal(false); load() }}
      />
    )}
    </>
  )
}

// ── Job row component ─────────────────────────────────────────────────────────

function JobRow({ job, onCancel, onRequeue }: {
  job: Job
  onCancel: (id: string) => void
  onRequeue: (id: string) => void
}) {
  const [expanded, setExpanded] = useState(false)
  const canCancel  = job.status === 'pending' || job.status === 'scheduled'
  const canRequeue = job.status === 'dead'    || job.status === 'failed'

  return (
    <>
      <tr style={styles.tr} onClick={() => setExpanded(e => !e)}>
        <td style={{ ...styles.td, fontFamily: 'var(--mono)', fontSize: 11 }}>
          {job.id.slice(0, 8)}…
        </td>
        <td style={styles.td}>
          <span className={`badge badge-${job.status}`}>{job.status}</span>
        </td>
        <td style={styles.td}>{job.priority}</td>
        <td style={styles.td}>{job.attemptCount}/{job.maxAttempts}</td>
        <td style={{ ...styles.td, color: 'var(--text-muted)' }}>
          {new Date(job.scheduledAt).toLocaleString()}
        </td>
        <td style={styles.td} onClick={e => e.stopPropagation()}>
          <div style={{ display: 'flex', gap: 6 }}>
            {canCancel && (
              <button className="btn-ghost" style={{ fontSize: 11, padding: '3px 7px' }}
                onClick={() => onCancel(job.id)}>Cancel</button>
            )}
            {canRequeue && (
              <button className="btn-ghost" style={{ fontSize: 11, padding: '3px 7px' }}
                onClick={() => onRequeue(job.id)}>Requeue</button>
            )}
          </div>
        </td>
      </tr>
      {expanded && (
        <tr>
          <td colSpan={6} style={{ padding: '12px 16px', background: 'var(--bg-hover)',
                                   borderBottom: '1px solid var(--border)' }}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
              <div>
                <p style={styles.detailLabel}>Payload</p>
                <pre style={styles.pre}>{JSON.stringify(job.payload, null, 2)}</pre>
              </div>
              {job.lastError && (
                <div>
                  <p style={styles.detailLabel}>Last Error</p>
                  <pre style={{ ...styles.pre, color: 'var(--danger)' }}>{job.lastError}</pre>
                </div>
              )}
              {job.lockedBy && (
                <div>
                  <p style={styles.detailLabel}>Locked By</p>
                  <code style={{ fontFamily: 'var(--mono)', fontSize: 12 }}>{job.lockedBy}</code>
                </div>
              )}
              <div>
                <p style={styles.detailLabel}>Full Job ID</p>
                <code style={{ fontFamily: 'var(--mono)', fontSize: 11,
                               color: 'var(--text-muted)' }}>{job.id}</code>
              </div>
            </div>
          </td>
        </tr>
      )}
    </>
  )
}

// ── Styles ────────────────────────────────────────────────────────────────────

const styles: Record<string, React.CSSProperties> = {
  page:          { maxWidth: 1100, margin: '0 auto', padding: '32px 24px' },
  header:        { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 24 },
  breadcrumb:    { display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 },
  crumbLink:     { color: 'var(--text-muted)', fontSize: 12 },
  crumbSep:      { color: 'var(--text-dim)', fontSize: 12 },
  pageTitle:     { fontSize: '22px', fontWeight: 700, marginBottom: 4 },
  queueMeta:     { display: 'flex', gap: 8, alignItems: 'center', marginTop: 4, flexWrap: 'wrap' },
  metaTag:       { background: 'var(--bg-hover)', borderRadius: 10, padding: '2px 8px',
                   fontSize: 11, color: 'var(--text-muted)' },
  label:         { display: 'block', fontSize: 12, color: 'var(--text-muted)', marginBottom: 6 },
  filterRow:     { display: 'flex', flexWrap: 'wrap' as const, gap: 6, marginBottom: 20 },
  table:         { width: '100%', borderCollapse: 'collapse', minWidth: 700 },
  th:            { textAlign: 'left', padding: '8px 12px', color: 'var(--text-muted)', fontSize: 11,
                   fontWeight: 600, textTransform: 'uppercase', borderBottom: '1px solid var(--border)' },
  td:            { padding: '10px 12px', borderBottom: '1px solid var(--border)', fontSize: 13, cursor: 'pointer' },
  tr:            {},
  pagination:    { display: 'flex', alignItems: 'center', justifyContent: 'center',
                   gap: 16, padding: '20px 0' },
  pre:           { fontFamily: 'var(--mono)', fontSize: 11, whiteSpace: 'pre-wrap', wordBreak: 'break-all',
                   background: 'var(--bg)', padding: 8, borderRadius: 6, maxHeight: 180, overflow: 'auto' },
  detailLabel:   { fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', marginBottom: 6,
                   textTransform: 'uppercase' },
}
