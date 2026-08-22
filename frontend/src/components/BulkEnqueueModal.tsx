import { useState, useEffect, useCallback } from 'react'
import { projectsApi } from '../api/projects'
import { queuesApi } from '../api/queues'
import { jobsApi } from '../api/jobs'
import type { Project, Queue } from '../types'

// ── Per-queue JSON template shown as hint ─────────────────────────────────────
const QUEUE_TEMPLATES: Record<string, object> = {
  email:              { to: 'user@example.com', subject: 'Hello', body: 'Message body' },
  emailjobexecutor:   { to: 'user@example.com', subject: 'Hello', body: 'Message body' },
  notification:       { userEmail: 'user@example.com', message: 'Your job is done', channel: 'in-app' },
  notificationjobexecutor: { userEmail: 'user@example.com', message: 'Your job is done', channel: 'in-app' },
  'demo-task':        { message: 'Hello from Demo Task' },
  demo:               { message: 'Hello from Demo Task' },
  demotaskexecutor:   { message: 'Hello from Demo Task' },
  calculate:          { operation: 'SUM', values: [10, 20, 30] },
  calculateexecutor:  { operation: 'SUM', values: [10, 20, 30] },
  'pdf-extract':      { fileName: 'document.pdf', fileUrl: 'https://example.com/document.pdf' },
  pdfextract:         { fileName: 'document.pdf', fileUrl: 'https://example.com/document.pdf' },
}

function getTemplate(queueName: string): object {
  const key = queueName.toLowerCase().replace(/[\s_-]+/g, '')
  const lower = queueName.toLowerCase()
  return QUEUE_TEMPLATES[lower] ?? QUEUE_TEMPLATES[key] ?? { key: 'value' }
}

function buildPlaceholder(queueName: string): string {
  const template = getTemplate(queueName)
  return JSON.stringify([template, template], null, 2)
}

// ── Props ─────────────────────────────────────────────────────────────────────

interface Props {
  /** If provided, skip project/queue selectors and go straight to the editor */
  lockedQueue?: Queue
  lockedProjectId?: string
  onClose: () => void
  onSuccess: (count: number) => void
}

// ── Validation result ─────────────────────────────────────────────────────────

interface ValidationResult {
  valid: boolean
  parsed: Record<string, unknown>[] | null
  errorLine: number | null
  errorMessage: string | null
}

function validateJson(text: string): ValidationResult {
  const trimmed = text.trim()
  if (!trimmed) {
    return { valid: false, parsed: null, errorLine: null, errorMessage: 'Paste a JSON array to continue.' }
  }
  try {
    const parsed = JSON.parse(trimmed)
    if (!Array.isArray(parsed)) {
      return { valid: false, parsed: null, errorLine: 1, errorMessage: 'Input must be a JSON array [ ... ].' }
    }
    if (parsed.length === 0) {
      return { valid: false, parsed: null, errorLine: 1, errorMessage: 'Array must contain at least one job.' }
    }
    if (parsed.length > 500) {
      return { valid: false, parsed: null, errorLine: null,
               errorMessage: `Too many jobs: ${parsed.length}. Maximum is 500 per batch.` }
    }
    for (let i = 0; i < parsed.length; i++) {
      if (typeof parsed[i] !== 'object' || parsed[i] === null || Array.isArray(parsed[i])) {
        return { valid: false, parsed: null, errorLine: null,
                 errorMessage: `Item at index ${i} is not an object. Each element must be a JSON object { }.` }
      }
    }
    return { valid: true, parsed: parsed as Record<string, unknown>[], errorLine: null, errorMessage: null }
  } catch (e: unknown) {
    // Try to extract line number from SyntaxError message
    const msg = e instanceof SyntaxError ? e.message : String(e)
    const lineMatch = msg.match(/line (\d+)/i)
    const line = lineMatch ? parseInt(lineMatch[1]) : null
    return { valid: false, parsed: null, errorLine: line, errorMessage: `JSON parse error: ${msg}` }
  }
}

// ── Submission state ──────────────────────────────────────────────────────────

type SubmitState =
  | { phase: 'idle' }
  | { phase: 'submitting' }
  | { phase: 'done'; enqueued: number }
  | { phase: 'error'; message: string }

// ── Component ─────────────────────────────────────────────────────────────────

export default function BulkEnqueueModal({
  lockedQueue, lockedProjectId, onClose, onSuccess,
}: Props) {
  const [projects, setProjects]     = useState<Project[]>([])
  const [queues, setQueues]         = useState<Queue[]>([])
  const [selectedProjectId, setSelectedProjectId] = useState(lockedProjectId ?? '')
  const [selectedQueueId, setSelectedQueueId]     = useState(lockedQueue?.id ?? '')
  const [loadingProjects, setLoadingProjects]     = useState(!lockedProjectId && !lockedQueue)
  const [loadingQueues, setLoadingQueues]         = useState(false)

  const [jsonText, setJsonText]   = useState('')
  const [priority, setPriority]   = useState(0)
  const [maxAttempts, setMaxAttempts] = useState(3)
  const [concurrency, setConcurrency] = useState(5)

  const [validation, setValidation] = useState<ValidationResult>(
    { valid: false, parsed: null, errorLine: null, errorMessage: null }
  )
  const [submitState, setSubmitState] = useState<SubmitState>({ phase: 'idle' })

  const selectedQueue = lockedQueue ?? queues.find(q => q.id === selectedQueueId) ?? null

  // Sync concurrency when queue changes
  useEffect(() => {
    if (selectedQueue?.concurrencyLimit) {
      setConcurrency(selectedQueue.concurrencyLimit)
    } else {
      setConcurrency(5)
    }
  }, [selectedQueue])


  // ── Load projects ─────────────────────────────────────────────────────────
  useEffect(() => {
    if (lockedProjectId || lockedQueue) return
    projectsApi.list().then(setProjects).finally(() => setLoadingProjects(false))
  }, [lockedProjectId, lockedQueue])

  // ── Load queues when project changes ─────────────────────────────────────
  const loadQueues = useCallback(async (projectId: string) => {
    if (!projectId) { setQueues([]); return }
    setLoadingQueues(true)
    try {
      const qs = await queuesApi.list(projectId)
      setQueues(qs)
      if (qs.length > 0 && !selectedQueueId) setSelectedQueueId(qs[0].id)
    } finally {
      setLoadingQueues(false)
    }
  }, [selectedQueueId])

  useEffect(() => {
    if (lockedQueue) return
    loadQueues(selectedProjectId)
  }, [selectedProjectId, lockedQueue, loadQueues])

  // ── Validate on every keystroke ───────────────────────────────────────────
  useEffect(() => {
    if (!jsonText.trim()) {
      setValidation({ valid: false, parsed: null, errorLine: null, errorMessage: null })
      return
    }
    setValidation(validateJson(jsonText))
  }, [jsonText])

  // Reset editor when queue changes
  useEffect(() => {
    setJsonText('')
    setSubmitState({ phase: 'idle' })
  }, [selectedQueueId])

  // ── Submit ────────────────────────────────────────────────────────────────
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedQueue || !validation.valid || !validation.parsed) return

    setSubmitState({ phase: 'submitting' })
    try {
      const result = await jobsApi.bulkEnqueue(selectedQueue.id, {
        payloads: validation.parsed,
        priority,
        maxAttempts,
        concurrency,
      })
      setSubmitState({ phase: 'done', enqueued: result.enqueued })
      onSuccess(result.enqueued)
    } catch (err: unknown) {

      const msg = (err as { response?: { data?: { detail?: string } } })
        ?.response?.data?.detail ?? 'Failed to enqueue jobs.'
      setSubmitState({ phase: 'error', message: msg })
    }
  }

  const isSubmitting  = submitState.phase === 'submitting'
  const isDone        = submitState.phase === 'done'
  const jobCount      = validation.parsed?.length ?? 0
  const canSubmit     = validation.valid && !!selectedQueue && !isSubmitting && !isDone

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div style={s.overlay} onClick={e => { if (e.target === e.currentTarget) onClose() }}>
      <div style={s.modal}>

        {/* ── Header ── */}
        <div style={s.header}>
          <div>
            <h2 style={s.title}>Bulk Enqueue Jobs</h2>
            <p style={s.subtitle}>
              Paste a JSON array — each element becomes one job
              {selectedQueue && <span> → <strong>{selectedQueue.name}</strong></span>}
            </p>
          </div>
          <button style={s.closeBtn} onClick={onClose} aria-label="Close">✕</button>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', flex: 1, overflow: 'hidden' }}>
          <div style={s.body}>

            {/* ── Step 1: Queue selector ── */}
            {!lockedQueue && (
              <section style={s.section}>
                <h3 style={s.stepLabel}><span style={s.stepBadge}>1</span> Select Queue</h3>
                <div style={s.row}>
                  {/* Project */}
                  {!lockedProjectId && (
                    <div style={{ flex: 1 }}>
                      <label style={s.fieldLabel}>Project</label>
                      {loadingProjects
                        ? <p style={s.loading}>Loading…</p>
                        : <select value={selectedProjectId} style={s.select} required
                            onChange={e => { setSelectedProjectId(e.target.value); setSelectedQueueId('') }}>
                            <option value="">— select project —</option>
                            {projects.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
                          </select>
                      }
                    </div>
                  )}
                  {/* Queue */}
                  <div style={{ flex: 1 }}>
                    <label style={s.fieldLabel}>Queue</label>
                    {loadingQueues
                      ? <p style={s.loading}>Loading…</p>
                      : <select value={selectedQueueId} style={s.select} required
                          disabled={!selectedProjectId}
                          onChange={e => setSelectedQueueId(e.target.value)}>
                          <option value="">— select queue —</option>
                          {queues.map(q => (
                            <option key={q.id} value={q.id}>
                              {q.name}{q.isPaused ? ' (paused)' : ''}
                            </option>
                          ))}
                        </select>
                    }
                  </div>
                </div>
              </section>
            )}

            {/* ── Step 2: Options ── */}
            {selectedQueue && (
              <section style={s.section}>
                <h3 style={s.stepLabel}>
                  <span style={s.stepBadge}>{lockedQueue ? '1' : '2'}</span> Options
                </h3>
                <div style={s.row}>
                  <div>
                    <label style={s.fieldLabel}>Priority <span style={s.pill}>0–100</span></label>
                    <input type="number" min={0} max={100} value={priority}
                      onChange={e => setPriority(Number(e.target.value))}
                      style={{ ...s.input, width: 80 }} />
                    <p style={s.hint}>Higher = picked up first</p>
                  </div>
                  <div>
                    <label style={s.fieldLabel}>Max Attempts <span style={s.pill}>1–20</span></label>
                    <input type="number" min={1} max={20} value={maxAttempts}
                      onChange={e => setMaxAttempts(Number(e.target.value))}
                      style={{ ...s.input, width: 80 }} />
                    <p style={s.hint}>Retries on failure</p>
                  </div>
                  <div>
                    <label style={s.fieldLabel}>Concurrency <span style={s.pill}>1–100</span></label>
                    <input type="number" min={1} max={100} value={concurrency}
                      onChange={e => setConcurrency(Number(e.target.value))}
                      style={{ ...s.input, width: 80 }} />
                    <p style={s.hint}>parallel limit (default: 5)</p>
                  </div>

                </div>
              </section>
            )}

            {/* ── Step 3: JSON Editor ── */}
            {selectedQueue && (
              <section style={{ ...s.section, flex: 1, display: 'flex', flexDirection: 'column' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 8 }}>
                  <h3 style={{ ...s.stepLabel, margin: 0 }}>
                    <span style={s.stepBadge}>{lockedQueue ? '2' : '3'}</span> Paste JSON Array
                  </h3>
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                    {/* Live counter */}
                    {validation.valid && (
                      <span style={s.countBadge}>{jobCount} job{jobCount !== 1 ? 's' : ''} ready</span>
                    )}
                    {/* Insert template button */}
                    <button type="button" style={s.templateBtn}
                      onClick={() => setJsonText(buildPlaceholder(selectedQueue.name))}>
                      Insert template
                    </button>
                  </div>
                </div>

                {/* Format hint */}
                <div style={s.formatHint}>
                  <span style={{ fontWeight: 600 }}>Format:</span> A JSON array where each object is
                  one job payload. Example for <code style={{ fontFamily: 'var(--mono)', fontSize: 11 }}>
                    {selectedQueue.name}</code>:
                  <pre style={s.templatePre}>
                    {JSON.stringify([getTemplate(selectedQueue.name)], null, 2)}
                  </pre>
                </div>

                {/* Editor textarea */}
                <div style={{ position: 'relative', flex: 1 }}>
                  <textarea
                    value={jsonText}
                    onChange={e => setJsonText(e.target.value)}
                    spellCheck={false}
                    placeholder={`[\n  ${JSON.stringify(getTemplate(selectedQueue.name))},\n  ${JSON.stringify(getTemplate(selectedQueue.name))}\n]`}
                    style={{
                      ...s.editor,
                      borderColor: jsonText && !validation.valid ? 'var(--danger)'
                                  : jsonText &&  validation.valid ? '#22c55e'
                                  : 'var(--border)',
                    }}
                  />
                  {/* Status indicator on editor */}
                  {jsonText && (
                    <div style={{
                      position: 'absolute', top: 10, right: 10,
                      fontSize: 11, fontWeight: 600,
                      color: validation.valid ? '#22c55e' : 'var(--danger)',
                    }}>
                      {validation.valid
                        ? `✓ Valid — ${jobCount} job${jobCount !== 1 ? 's' : ''}`
                        : '✗ Invalid JSON'}
                    </div>
                  )}
                </div>

                {/* Error message */}
                {validation.errorMessage && (
                  <div style={s.errorBox}>
                    <span style={{ fontWeight: 600 }}>
                      {validation.errorLine ? `Line ${validation.errorLine}: ` : ''}
                    </span>
                    {validation.errorMessage}
                  </div>
                )}
              </section>
            )}

            {/* Done / Error states */}
            {submitState.phase === 'done' && (
              <div style={s.successBox}>
                ✓ Successfully enqueued <strong>{submitState.enqueued}</strong> job
                {submitState.enqueued !== 1 ? 's' : ''} onto <strong>{selectedQueue?.name}</strong>.
              </div>
            )}
            {submitState.phase === 'error' && (
              <div style={s.errorBox}>{submitState.message}</div>
            )}
          </div>

          {/* ── Footer ── */}
          <div style={s.footer}>
            <button type="button" className="btn-ghost" onClick={onClose}>
              {isDone ? 'Close' : 'Cancel'}
            </button>
            {!isDone && (
              <button
                type="submit"
                className="btn-primary"
                disabled={!canSubmit}
                style={{ minWidth: 160 }}
              >
                {isSubmitting
                  ? `Enqueueing ${jobCount} job${jobCount !== 1 ? 's' : ''}…`
                  : canSubmit
                    ? `Enqueue ${jobCount} Job${jobCount !== 1 ? 's' : ''}`
                    : 'Enqueue Jobs'}
              </button>
            )}
          </div>
        </form>
      </div>
    </div>
  )
}

// ── Styles ────────────────────────────────────────────────────────────────────

const s: Record<string, React.CSSProperties> = {
  overlay: {
    position: 'fixed', inset: 0,
    background: 'rgba(0,0,0,0.65)',
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    zIndex: 1000, padding: 16,
  },
  modal: {
    background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: 14,
    width: '100%', maxWidth: 720, height: '88vh',
    display: 'flex', flexDirection: 'column',
    boxShadow: 'var(--shadow)', overflow: 'hidden',
  },
  header: {
    display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start',
    padding: '18px 24px 14px', borderBottom: '1px solid var(--border)', flexShrink: 0,
  },
  title:    { fontSize: 16, fontWeight: 700, margin: 0 },
  subtitle: { fontSize: 12, color: 'var(--text-muted)', margin: '3px 0 0' },
  closeBtn: {
    background: 'transparent', border: 'none', color: 'var(--text-muted)',
    fontSize: 16, cursor: 'pointer', padding: '4px 6px', borderRadius: 6,
  },
  body: {
    flex: 1, overflowY: 'auto', padding: '16px 24px',
    display: 'flex', flexDirection: 'column', gap: 0,
  },
  footer: {
    display: 'flex', justifyContent: 'flex-end', gap: 8,
    padding: '12px 24px', borderTop: '1px solid var(--border)', flexShrink: 0,
  },
  section: {
    marginBottom: 20,
  },
  stepLabel: {
    display: 'flex', alignItems: 'center', gap: 8,
    fontSize: 13, fontWeight: 700, color: 'var(--text)',
    marginBottom: 12,
  },
  stepBadge: {
    width: 20, height: 20, borderRadius: '50%',
    background: 'var(--accent)', color: '#fff',
    fontSize: 11, fontWeight: 700,
    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
    flexShrink: 0,
  },
  row: { display: 'flex', gap: 20, flexWrap: 'wrap' as const },
  fieldLabel: {
    display: 'block', fontSize: 11, fontWeight: 600,
    color: 'var(--text-muted)', marginBottom: 5,
    textTransform: 'uppercase', letterSpacing: '0.04em',
  },
  pill: {
    fontSize: 10, background: 'var(--bg-hover)', color: 'var(--text-dim)',
    borderRadius: 4, padding: '1px 5px', fontWeight: 400,
    textTransform: 'none', marginLeft: 4,
  },
  hint:   { fontSize: 11, color: 'var(--text-dim)', marginTop: 4 },
  input: {
    padding: '8px 10px',
    background: 'var(--bg)', color: 'var(--text)',
    border: '1px solid var(--border)', borderRadius: 7,
    fontSize: 13, outline: 'none',
  },
  select: {
    width: '100%', padding: '8px 10px',
    background: 'var(--bg)', color: 'var(--text)',
    border: '1px solid var(--border)', borderRadius: 7,
    fontSize: 13, cursor: 'pointer',
  },
  loading: { fontSize: 13, color: 'var(--text-muted)', margin: 0 },
  templateBtn: {
    background: 'rgba(99,102,241,0.1)', border: '1px solid rgba(99,102,241,0.25)',
    color: 'var(--accent)', borderRadius: 6, padding: '4px 10px',
    fontSize: 11, cursor: 'pointer', fontWeight: 600,
  },
  countBadge: {
    background: 'rgba(34,197,94,0.15)', color: '#16a34a',
    borderRadius: 6, padding: '3px 8px', fontSize: 11, fontWeight: 700,
  },
  formatHint: {
    background: 'var(--bg)', border: '1px solid var(--border)', borderRadius: 8,
    padding: '10px 14px', fontSize: 12, color: 'var(--text-muted)',
    marginBottom: 10, flexShrink: 0,
  },
  templatePre: {
    fontFamily: 'var(--mono)', fontSize: 11, margin: '6px 0 0',
    color: 'var(--text)', whiteSpace: 'pre',
  },
  editor: {
    width: '100%', minHeight: 240,
    fontFamily: 'var(--mono)', fontSize: 12, lineHeight: 1.5,
    background: '#0d1117', color: '#e6edf3',
    border: '2px solid var(--border)', borderRadius: 8,
    padding: '12px 14px', resize: 'vertical',
    outline: 'none', boxSizing: 'border-box' as const,
    transition: 'border-color 0.15s',
  },
  errorBox: {
    background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.3)',
    borderRadius: 6, padding: '8px 12px',
    fontSize: 12, color: 'var(--danger)', marginTop: 8,
    fontFamily: 'var(--mono)',
  },
  successBox: {
    background: 'rgba(34,197,94,0.08)', border: '1px solid rgba(34,197,94,0.3)',
    borderRadius: 8, padding: '14px 16px', fontSize: 13, color: '#16a34a',
    marginTop: 8,
  },
}
