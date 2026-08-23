import { useState, useCallback } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { projectsApi } from '../api/projects'
import { queuesApi } from '../api/queues'
import { jobsApi } from '../api/jobs'
import { usePolling } from '../hooks/usePolling'
import { useSessionReady } from '../hooks/useSessionReady'
import { findBySlug } from '../lib/slug'
import EnqueueJobModal from '../components/EnqueueJobModal'
import BulkEnqueueModal from '../components/BulkEnqueueModal'
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
  const [showBulkModal, setShowBulkModal]       = useState(false)

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
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn-ghost"
              style={{ fontSize: 13, borderColor: 'rgba(99,102,241,0.4)', color: 'var(--accent)' }}
              onClick={() => setShowBulkModal(true)}>
              ⚡ Bulk 
            </button>
            <button className="btn-primary" onClick={() => setShowEnqueueModal(true)}>
              + Enqueue Job
            </button>
          </div>
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

    {/* Bulk Enqueue modal */}
    {showBulkModal && queue && (
      <BulkEnqueueModal
        lockedQueue={queue}
        onClose={() => setShowBulkModal(false)}
        onSuccess={() => { setShowBulkModal(false); load() }}
      />
    )}
    </>
  )
}

// ── Job row component ─────────────────────────────────────────────────────────

/** Renders the result card for any executor type that stores a result. */
function JobResult({ result }: { result: Record<string, unknown> }) {
  // ── Calculate executor ─────────────────────────────────────────────────────
  if ('operation' in result && 'values' in result && 'result' in result) {
    return (
      <div style={{ gridColumn: '1 / -1', ...resultCard('#16a34a', 'rgba(34,197,94,0.07)', 'rgba(34,197,94,0.3)') }}>
        <p style={{ ...resultLabel, color: '#16a34a' }}>Calculation Result</p>
        <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap', alignItems: 'flex-end' }}>
          <ResultKV label="Operation" value={String(result.operation)} mono accent />
          <ResultKV label="Values"    value={`[${(result.values as number[]).join(', ')}]`} mono />
          <div>
            <span style={resultKvLabel}>Result</span>
            <div style={{ fontFamily: 'var(--mono)', fontWeight: 800, fontSize: 26, color: '#16a34a' }}>
              {Number(result.result).toLocaleString(undefined, { maximumFractionDigits: 10 })}
            </div>
          </div>
        </div>
      </div>
    )
  }

  // ── Demo Task executor ────────────────────────────────────────────────────
  if ('message' in result && 'durationMs' in result) {
    return (
      <div style={{ gridColumn: '1 / -1', ...resultCard('#6366f1', 'rgba(99,102,241,0.07)', 'rgba(99,102,241,0.3)') }}>
        <p style={{ ...resultLabel, color: '#6366f1' }}>Demo Task Completed</p>
        <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap', alignItems: 'flex-start' }}>
          <ResultKV label="Status"   value={String(result.status ?? 'completed')} />
          <ResultKV label="Duration" value={`${result.durationMs} ms`} mono />
          <div style={{ flex: 1, minWidth: 180 }}>
            <span style={resultKvLabel}>Message</span>
            <div style={{ fontSize: 14, fontStyle: 'italic', color: 'var(--text)', marginTop: 2 }}>
              "{String(result.message)}"
            </div>
          </div>
        </div>
      </div>
    )
  }

  // ── Notification executor ─────────────────────────────────────────────────
  if ('channel' in result && 'deliveredAt' in result) {
    const channelColor: Record<string, string> = {
      'push': '#f59e0b', 'sms': '#3b82f6', 'in-app': '#10b981'
    }
    const ch = String(result.channel ?? 'in-app')
    return (
      <div style={{ gridColumn: '1 / -1', ...resultCard(channelColor[ch] ?? '#10b981', 'rgba(16,185,129,0.07)', 'rgba(16,185,129,0.3)') }}>
        <p style={{ ...resultLabel, color: channelColor[ch] ?? '#10b981' }}>Notification Delivered</p>
        <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap', alignItems: 'flex-start' }}>
          <ResultKV label="Status"  value={String(result.status ?? 'delivered')} />
          <ResultKV label={result.userEmail ? 'Recipient' : 'User ID'}
                    value={String(result.userEmail ?? result.userId ?? '—')} mono />
          <ResultKV label="Channel" value={ch.toUpperCase()} mono accent />
          <div style={{ flex: 1, minWidth: 180 }}>
            <span style={resultKvLabel}>Message</span>
            <div style={{ fontSize: 13, color: 'var(--text)', marginTop: 2 }}>
              {String(result.message)}
            </div>
          </div>
        </div>
        <div style={{ fontSize: 11, color: 'var(--text-dim)', marginTop: 8 }}>
          Delivered at {new Date(String(result.deliveredAt)).toLocaleString()}
          {' — '}Stored in recipient&apos;s inbox
        </div>
      </div>
    )
  }

  // ── PDF executor ──────────────────────────────────────────────────────────
  if ('summary' in result && 'pageCount' in result) {
    return (
      <div style={{ gridColumn: '1 / -1', ...resultCard('#f97316', 'rgba(249,115,22,0.07)', 'rgba(249,115,22,0.3)') }}>
        <p style={{ ...resultLabel, color: '#f97316' }}>PDF Extraction Complete</p>
        <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap', marginBottom: 10 }}>
          <ResultKV label="File"      value={String(result.fileName)} mono />
          <ResultKV label="Pages"     value={String(result.pageCount)} mono />
          <ResultKV label="Words"     value={Number(result.wordCount).toLocaleString()} mono />
          <ResultKV label="Size"      value={`${(Number(result.sizeBytes) / 1024).toFixed(1)} KB`} mono />
        </div>
        <div>
          <span style={resultKvLabel}>Extracted Summary</span>
          <pre style={{
            marginTop: 6, fontFamily: 'var(--mono)', fontSize: 11,
            background: 'var(--bg)', padding: '8px 10px', borderRadius: 6,
            whiteSpace: 'pre-wrap', wordBreak: 'break-word',
            maxHeight: 160, overflowY: 'auto', color: 'var(--text)',
            border: '1px solid var(--border)',
          }}>
            {String(result.summary)}
          </pre>
        </div>
      </div>
    )
  }

  // ── Generic fallback ──────────────────────────────────────────────────────
  return (
    <div style={{ gridColumn: '1 / -1', ...resultCard('#6366f1', 'rgba(99,102,241,0.07)', 'rgba(99,102,241,0.3)') }}>
      <p style={{ ...resultLabel, color: '#6366f1' }}>Job Result</p>
      <pre style={{ fontFamily: 'var(--mono)', fontSize: 11, margin: 0 }}>
        {JSON.stringify(result, null, 2)}
      </pre>
    </div>
  )
}

// Helpers for result cards
function resultCard(_accent: string, bg: string, border: string): React.CSSProperties {
  return { background: bg, border: `1px solid ${border}`, borderRadius: 8, padding: '12px 14px' }
}

const resultLabel: React.CSSProperties = { fontSize: 11, fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 10 }
const resultKvLabel: React.CSSProperties = { fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', display: 'block' }

function ResultKV({ label, value, mono, accent }: { label: string; value: string; mono?: boolean; accent?: boolean }) {
  return (
    <div>
      <span style={resultKvLabel}>{label}</span>
      <div style={{ fontFamily: mono ? 'var(--mono)' : undefined, fontWeight: 600, fontSize: 14,
                    color: accent ? 'var(--accent)' : 'var(--text)', marginTop: 2 }}>
        {value}
      </div>
    </div>
  )
}

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

              {/* Job result — adapts per executor type */}
              {job.result && <JobResult result={job.result} />}

              {/* AI Failure Summary Card */}
              {(job.status === 'failed' || job.status === 'dead' || job.lastError || job.failureSummary) && (
                <AiFailureSummaryCard job={job} onRequeue={onRequeue} />
              )}

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

function AiFailureSummaryCard({ job, onRequeue }: { job: Job; onRequeue: (id: string) => void }) {
  const [summary, setSummary] = useState<string | null>(job.failureSummary ?? null)
  const [loading, setLoading] = useState(false)

  const handleGenerate = async () => {
    setLoading(true)
    try {
      const res = await jobsApi.getAiSummary(job.id)
      setSummary(res)
    } catch {
      setSummary('Failed to generate AI summary.')
    } finally {
      setLoading(false)
    }
  }

  const activeSummary = summary || job.failureSummary

  return (
    <div style={{
      gridColumn: '1 / -1',
      background: 'linear-gradient(135deg, rgba(99,102,241,0.08) 0%, rgba(239,68,68,0.08) 100%)',
      border: '1px solid rgba(99,102,241,0.3)',
      borderRadius: 10,
      padding: '14px 18px',
      marginTop: 4,
      position: 'relative',
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span style={{ fontSize: 16 }}>🤖</span>
          <span style={{ fontSize: 12, fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--accent)' }}>
            AI Failure Diagnostic
          </span>
          <span style={{ fontSize: 10, background: 'rgba(99,102,241,0.2)', color: 'var(--accent)', padding: '2px 6px', borderRadius: 4, fontWeight: 600 }}>
            Automated Analysis
          </span>
        </div>
        {!activeSummary && (
          <button className="btn-ghost" style={{ fontSize: 11, padding: '3px 9px', borderColor: 'var(--accent)', color: 'var(--accent)' }}
            disabled={loading} onClick={handleGenerate}>
            {loading ? 'Analyzing…' : '✨ Generate AI Summary'}
          </button>
        )}
      </div>

      {activeSummary ? (
        <div style={{ fontSize: 12, lineHeight: 1.6, color: 'var(--text)', whiteSpace: 'pre-wrap', fontFamily: 'sans-serif' }}>
          {activeSummary}
        </div>
      ) : (
        <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
          Click <strong>Generate AI Summary</strong> to run automated diagnostics on this failure.
        </div>
      )}

      {(job.status === 'dead' || job.status === 'failed') && (
        <div style={{ marginTop: 12, paddingTop: 10, borderTop: '1px solid rgba(255,255,255,0.08)', display: 'flex', justifyContent: 'flex-end' }}>
          <button className="btn-primary" style={{ fontSize: 11, padding: '4px 12px' }} onClick={() => onRequeue(job.id)}>
            🔄 Re-queue Job with AI Fix
          </button>
        </div>
      )}
    </div>
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
