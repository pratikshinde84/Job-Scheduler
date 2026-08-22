import { useState, useEffect, useCallback } from 'react'
import { projectsApi } from '../api/projects'
import { queuesApi } from '../api/queues'
import { jobsApi } from '../api/jobs'
import type { Project, Queue } from '../types'

// ── Executor field definitions ────────────────────────────────────────────────

interface FieldDef {
  key: string
  label: string
  type: 'text' | 'email' | 'number' | 'boolean' | 'select' | 'textarea'
  placeholder?: string
  options?: string[]
  defaultValue?: unknown
  required?: boolean
  hint?: string
  schemaType?: string   // shown in schema panel e.g. "string", "number", "boolean"
}

interface ExecutorDef {
  title: string
  description: string
  fields: FieldDef[]
}

/**
 * All queue name variants that map to each executor.
 * Covers both the exact executor class name AND common short names
 * users might create (e.g. "email", "Email", "Demo-Task", "demo_task" etc.)
 */
const EXECUTOR_DEFS: Record<string, ExecutorDef> = {
  // ── Email ──────────────────────────────────────────────────────────────────
  email: {
    title: 'Email Job',
    description: 'Simulates sending an email to a recipient via SMTP.',
    fields: [
      { key: 'to',      label: 'To',      type: 'email',    placeholder: 'user@example.com',      required: true,  schemaType: 'string (email)' },
      { key: 'subject', label: 'Subject', type: 'text',     placeholder: 'Welcome!',               required: true,  schemaType: 'string' },
      { key: 'body',    label: 'Body',    type: 'textarea', placeholder: 'Email body text…',       required: true,  schemaType: 'string' },
    ],
  },
  emailjobexecutor: { title: 'Email Job', description: 'Simulates sending an email to a recipient via SMTP.',
    fields: [
      { key: 'to',      label: 'To',      type: 'email',    placeholder: 'user@example.com', required: true, schemaType: 'string (email)' },
      { key: 'subject', label: 'Subject', type: 'text',     placeholder: 'Welcome!',          required: true, schemaType: 'string' },
      { key: 'body',    label: 'Body',    type: 'textarea', placeholder: 'Email body text…',  required: true, schemaType: 'string' },
    ],
  },

  // ── Notification ───────────────────────────────────────────────────────────
  notification: {
    title: 'Notification Job',
    description: 'Sends a push, SMS, or in-app notification to a user.',
    fields: [
      { key: 'userId',  label: 'User ID', type: 'number',   placeholder: '101',                    required: true,  schemaType: 'number' },
      { key: 'message', label: 'Message', type: 'textarea', placeholder: 'Your job is completed',  required: true,  schemaType: 'string' },
      { key: 'channel', label: 'Channel', type: 'select',   options: ['in-app', 'push', 'sms'],    defaultValue: 'in-app', schemaType: '"in-app" | "push" | "sms"' },
    ],
  },
  notificationjobexecutor: { title: 'Notification Job', description: 'Sends a push, SMS, or in-app notification to a user.',
    fields: [
      { key: 'userId',  label: 'User ID', type: 'number',   placeholder: '101',                   required: true, schemaType: 'number' },
      { key: 'message', label: 'Message', type: 'textarea', placeholder: 'Your job is completed', required: true, schemaType: 'string' },
      { key: 'channel', label: 'Channel', type: 'select',   options: ['in-app', 'push', 'sms'],   defaultValue: 'in-app', schemaType: '"in-app" | "push" | "sms"' },
    ],
  },

  // ── Demo Task ──────────────────────────────────────────────────────────────
  demo: {
    title: 'Demo Task',
    description: 'Simulates a long-running task to test worker execution, concurrency, and retries.',
    fields: [
      { key: 'duration',   label: 'Duration (seconds)', type: 'number',  defaultValue: 10,   schemaType: 'number',
        hint: 'How long the task should run. Each second = 2 work steps of 500 ms.' },
      { key: 'shouldFail', label: 'Should Fail',         type: 'boolean', defaultValue: false, schemaType: 'boolean',
        hint: 'If enabled, the job throws an error after running to trigger retry logic.' },
    ],
  },
  'demo-task': {
    title: 'Demo Task', description: 'Simulates a long-running task to test worker execution, concurrency, and retries.',
    fields: [
      { key: 'duration',   label: 'Duration (seconds)', type: 'number',  defaultValue: 10,    schemaType: 'number',  hint: 'How long the task should run.' },
      { key: 'shouldFail', label: 'Should Fail',         type: 'boolean', defaultValue: false, schemaType: 'boolean', hint: 'Throw an error to trigger retry logic.' },
    ],
  },
  demotask: {
    title: 'Demo Task', description: 'Simulates a long-running task to test worker execution, concurrency, and retries.',
    fields: [
      { key: 'duration',   label: 'Duration (seconds)', type: 'number',  defaultValue: 10,    schemaType: 'number' },
      { key: 'shouldFail', label: 'Should Fail',         type: 'boolean', defaultValue: false, schemaType: 'boolean' },
    ],
  },
  demotaskexecutor: {
    title: 'Demo Task', description: 'Simulates a long-running task to test worker execution, concurrency, and retries.',
    fields: [
      { key: 'duration',   label: 'Duration (seconds)', type: 'number',  defaultValue: 10,    schemaType: 'number' },
      { key: 'shouldFail', label: 'Should Fail',         type: 'boolean', defaultValue: false, schemaType: 'boolean' },
    ],
  },

  // ── PDF ────────────────────────────────────────────────────────────────────
  pdf: {
    title: 'PDF Processor',
    description: 'Downloads a PDF from a URL, validates it, and extracts its content.',
    fields: [
      { key: 'fileName', label: 'File Name', type: 'text', placeholder: 'resume.pdf',                       required: true, schemaType: 'string' },
      { key: 'fileUrl',  label: 'File URL',  type: 'text', placeholder: 'https://example.com/resume.pdf',  required: true, schemaType: 'string (URL)',
        hint: 'Must be a publicly accessible URL. Max file size: 20 MB.' },
    ],
  },
  'pdf-extract': {
    title: 'PDF Processor', description: 'Downloads a PDF from a URL, validates it, and extracts its content.',
    fields: [
      { key: 'fileName', label: 'File Name', type: 'text', placeholder: 'resume.pdf',                      required: true, schemaType: 'string' },
      { key: 'fileUrl',  label: 'File URL',  type: 'text', placeholder: 'https://example.com/resume.pdf', required: true, schemaType: 'string (URL)', hint: 'Max size: 20 MB.' },
    ],
  },
  pdfextract: {
    title: 'PDF Processor', description: 'Downloads a PDF from a URL, validates it, and extracts its content.',
    fields: [
      { key: 'fileName', label: 'File Name', type: 'text', placeholder: 'resume.pdf',                      required: true, schemaType: 'string' },
      { key: 'fileUrl',  label: 'File URL',  type: 'text', placeholder: 'https://example.com/resume.pdf', required: true, schemaType: 'string (URL)' },
    ],
  },
  pdfjobexecutor: {
    title: 'PDF Processor', description: 'Downloads a PDF from a URL, validates it, and extracts its content.',
    fields: [
      { key: 'fileName', label: 'File Name', type: 'text', placeholder: 'resume.pdf',                      required: true, schemaType: 'string' },
      { key: 'fileUrl',  label: 'File URL',  type: 'text', placeholder: 'https://example.com/resume.pdf', required: true, schemaType: 'string (URL)' },
    ],
  },

  // ── Calculate ──────────────────────────────────────────────────────────────
  calculate: {
    title: 'Calculator',
    description: 'Performs a mathematical operation on a list of numbers.',
    fields: [
      { key: 'operation', label: 'Operation', type: 'select',
        options: ['SUM', 'AVERAGE', 'MIN', 'MAX', 'MULTIPLY'], defaultValue: 'SUM',
        schemaType: '"SUM" | "AVERAGE" | "MIN" | "MAX" | "MULTIPLY"' },
      { key: 'values', label: 'Values (comma-separated)', type: 'text',
        placeholder: '10, 20, 30, 40', required: true, schemaType: 'number[]',
        hint: 'Enter numbers separated by commas — they are sent as an array.' },
    ],
  },
  calculateexecutor: {
    title: 'Calculator', description: 'Performs a mathematical operation on a list of numbers.',
    fields: [
      { key: 'operation', label: 'Operation', type: 'select',
        options: ['SUM', 'AVERAGE', 'MIN', 'MAX', 'MULTIPLY'], defaultValue: 'SUM', schemaType: '"SUM" | "AVERAGE" | "MIN" | "MAX" | "MULTIPLY"' },
      { key: 'values', label: 'Values (comma-separated)', type: 'text',
        placeholder: '10, 20, 30, 40', required: true, schemaType: 'number[]',
        hint: 'Enter numbers separated by commas.' },
    ],
  },
}

/** Normalise a queue name to a lookup key: lower-case, strip spaces/underscores/hyphens */
function toKey(name: string) {
  return name.toLowerCase().replace(/[\s_-]+/g, '')
}

/** Find the executor def for a queue name, trying both exact and normalised forms */
function getExecutorDef(queueName: string): ExecutorDef | null {
  const lower = queueName.toLowerCase()
  const norm  = toKey(queueName)
  return EXECUTOR_DEFS[lower] ?? EXECUTOR_DEFS[norm] ?? null
}

// ── JSON template builder ─────────────────────────────────────────────────────
const DEFAULT_PAYLOAD = '{\n  \n}'

function buildJsonTemplate(queueName: string): string {
  const def = getExecutorDef(queueName)
  if (!def) return DEFAULT_PAYLOAD
  const obj: Record<string, unknown> = {}
  def.fields.forEach(f => {
    if (f.key === 'values') { obj[f.key] = [10, 20, 30]; return }
    obj[f.key] = f.defaultValue ?? (f.type === 'number' ? 0 : f.type === 'boolean' ? false : '')
  })
  return JSON.stringify(obj, null, 2)
}

// ── Props ─────────────────────────────────────────────────────────────────────

interface Props {
  lockedQueue?: Queue
  lockedProjectId?: string
  onClose: () => void
  onSuccess: () => void
}

// ── Component ─────────────────────────────────────────────────────────────────

export default function EnqueueJobModal({ lockedQueue, lockedProjectId, onClose, onSuccess }: Props) {
  const [projects, setProjects]                   = useState<Project[]>([])
  const [queues, setQueues]                       = useState<Queue[]>([])
  const [selectedProjectId, setSelectedProjectId] = useState<string>(lockedProjectId ?? '')
  const [selectedQueueId, setSelectedQueueId]     = useState<string>(lockedQueue?.id ?? '')
  const [loadingProjects, setLoadingProjects]     = useState(!lockedProjectId && !lockedQueue)
  const [loadingQueues, setLoadingQueues]         = useState(false)

  const [fieldValues, setFieldValues] = useState<Record<string, unknown>>({})
  const [rawJson, setRawJson]         = useState(DEFAULT_PAYLOAD)
  const [jsonError, setJsonError]     = useState<string | null>(null)
  const [showSchema, setShowSchema]   = useState(false)
  const [priority, setPriority]       = useState(0)
  const [maxAttempts, setMaxAttempts] = useState(3)
  const [submitting, setSubmitting]   = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const selectedQueue   = lockedQueue ?? queues.find(q => q.id === selectedQueueId) ?? null
  const executorDef     = selectedQueue ? getExecutorDef(selectedQueue.name) : null
  const isExecutorQueue = !!executorDef

  // Load projects
  useEffect(() => {
    if (lockedProjectId || lockedQueue) return
    projectsApi.list().then(setProjects).finally(() => setLoadingProjects(false))
  }, [lockedProjectId, lockedQueue])

  // Load queues when project selected
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

  // Reset form when queue changes
  useEffect(() => {
    const def = selectedQueue ? getExecutorDef(selectedQueue.name) : null
    if (def) {
      const defaults: Record<string, unknown> = {}
      def.fields.forEach(f => {
        defaults[f.key] = f.key === 'values' ? '10, 20, 30' : (f.defaultValue ?? '')
      })
      setFieldValues(defaults)
    } else {
      setRawJson(buildJsonTemplate(selectedQueue?.name ?? ''))
    }
    setJsonError(null)
    setSubmitError(null)
    setShowSchema(false)
  }, [selectedQueueId, selectedQueue?.name])

  // Build payload from field values
  function buildPayload(): Record<string, unknown> | null {
    if (isExecutorQueue) {
      const result: Record<string, unknown> = {}
      for (const field of executorDef!.fields) {
        let val = fieldValues[field.key]
        if (field.type === 'number') val = Number(val) || 0
        if (field.type === 'boolean') val = Boolean(val)
        if (field.key === 'values' && typeof val === 'string') {
          val = val.split(',').map(v => Number(v.trim())).filter(n => !isNaN(n))
        }
        result[field.key] = val
      }
      return result
    }
    try { return JSON.parse(rawJson) }
    catch { setJsonError('Payload must be valid JSON.'); return null }
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedQueue) return
    setSubmitError(null); setJsonError(null)
    const payload = buildPayload()
    if (!payload) return
    setSubmitting(true)
    try {
      await jobsApi.enqueue(selectedQueue.id, { payload, maxAttempts, priority })
      onSuccess(); onClose()
    } catch {
      setSubmitError('Failed to enqueue job. Check that the queue is active.')
    } finally {
      setSubmitting(false)
    }
  }

  // ── Field renderer ────────────────────────────────────────────────────────
  function renderField(field: FieldDef) {
    const value  = fieldValues[field.key] ?? field.defaultValue ?? ''
    const update = (v: unknown) => setFieldValues(prev => ({ ...prev, [field.key]: v }))

    return (
      <div key={field.key} style={s.fieldGroup}>
        <label style={s.fieldLabel}>
          {field.label}
          {field.required && <span style={{ color: 'var(--danger)', marginLeft: 3 }}>*</span>}
          <span style={s.schemaTypePill}>{field.schemaType ?? field.type}</span>
        </label>

        {field.type === 'select' && (
          <select value={String(value)} onChange={e => update(e.target.value)} style={s.select}>
            {field.options!.map(opt => <option key={opt} value={opt}>{opt}</option>)}
          </select>
        )}

        {field.type === 'boolean' && (
          <label style={s.toggleLabel}>
            <input type="checkbox" checked={Boolean(value)}
              onChange={e => update(e.target.checked)} style={{ display: 'none' }} />
            <span style={{ ...s.toggleTrack, background: Boolean(value) ? 'var(--accent)' : 'var(--border)' }}>
              <span style={{ ...s.toggleThumb, transform: Boolean(value) ? 'translateX(20px)' : 'translateX(2px)' }} />
            </span>
            <span style={{ fontSize: 13, color: 'var(--text)' }}>{Boolean(value) ? 'Yes' : 'No'}</span>
          </label>
        )}

        {field.type === 'textarea' && (
          <textarea value={String(value)} onChange={e => update(e.target.value)}
            placeholder={field.placeholder} rows={3} required={field.required}
            style={{ ...s.input, resize: 'vertical', minHeight: 72 }} />
        )}

        {(field.type === 'text' || field.type === 'email' || field.type === 'number') && (
          <input type={field.type} value={String(value)} placeholder={field.placeholder}
            required={field.required}
            onChange={e => update(field.type === 'number' ? e.target.valueAsNumber : e.target.value)}
            style={s.input} />
        )}

        {field.hint && <p style={s.fieldHint}>{field.hint}</p>}
      </div>
    )
  }

  // ── Schema panel ──────────────────────────────────────────────────────────
  function renderSchema() {
    if (!executorDef) return null
    return (
      <div style={s.schemaPanel}>
        <div style={s.schemaPanelHeader}>
          <span style={s.schemaPanelTitle}>Payload Schema — {executorDef.title}</span>
          <button type="button" style={s.schemaCloseBtn} onClick={() => setShowSchema(false)}>✕</button>
        </div>
        <table style={s.schemaTable}>
          <thead>
            <tr>
              <th style={s.schemaTh}>Field</th>
              <th style={s.schemaTh}>Type</th>
              <th style={s.schemaTh}>Required</th>
              <th style={s.schemaTh}>Notes</th>
            </tr>
          </thead>
          <tbody>
            {executorDef.fields.map(f => (
              <tr key={f.key}>
                <td style={{ ...s.schemaTd, fontFamily: 'var(--mono)', fontWeight: 600 }}>{f.key}</td>
                <td style={{ ...s.schemaTd, color: 'var(--accent)' }}>{f.schemaType ?? f.type}</td>
                <td style={s.schemaTd}>{f.required ? '✓' : '—'}</td>
                <td style={{ ...s.schemaTd, color: 'var(--text-muted)' }}>
                  {f.options ? `Options: ${f.options.join(', ')}` : (f.hint ?? f.placeholder ?? '—')}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {/* JSON example */}
        <p style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 6, marginTop: 10, fontWeight: 600 }}>
          EXAMPLE PAYLOAD
        </p>
        <pre style={s.schemaExample}>{buildJsonTemplate(selectedQueue!.name)}</pre>
      </div>
    )
  }

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div style={s.overlay} onClick={e => { if (e.target === e.currentTarget) onClose() }}>
      <div style={s.modal}>

        {/* ── Header ── */}
        <div style={s.modalHeader}>
          <div>
            <h2 style={s.modalTitle}>Enqueue New Job</h2>
            {selectedQueue && (
              <p style={s.modalSubtitle}>
                Queue: <strong>{selectedQueue.name}</strong>
                {executorDef && <span style={s.executorBadge}>{executorDef.title}</span>}
              </p>
            )}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            {isExecutorQueue && (
              <button type="button" style={s.schemaBtn} onClick={() => setShowSchema(v => !v)}>
                {showSchema ? 'Hide Schema' : '{ } Schema'}
              </button>
            )}
            <button style={s.closeBtn} onClick={onClose} aria-label="Close">✕</button>
          </div>
        </div>

        {/* ── Schema panel (slides in below header) ── */}
        {showSchema && renderSchema()}

        <form onSubmit={handleSubmit}>
          <div style={s.modalBody}>

            {/* Project selector */}
            {!lockedProjectId && !lockedQueue && (
              <div style={s.fieldGroup}>
                <label style={s.fieldLabel}>Project</label>
                {loadingProjects
                  ? <p style={s.loading}>Loading projects…</p>
                  : <select value={selectedProjectId} required style={s.select}
                      onChange={e => { setSelectedProjectId(e.target.value); setSelectedQueueId('') }}>
                      <option value="">— select a project —</option>
                      {projects.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
                    </select>
                }
              </div>
            )}

            {/* Queue selector */}
            {!lockedQueue && (
              <div style={s.fieldGroup}>
                <label style={s.fieldLabel}>Queue</label>
                {loadingQueues
                  ? <p style={s.loading}>Loading queues…</p>
                  : <select value={selectedQueueId} required disabled={!selectedProjectId} style={s.select}
                      onChange={e => setSelectedQueueId(e.target.value)}>
                      <option value="">— select a queue —</option>
                      {queues.map(q => (
                        <option key={q.id} value={q.id}>
                          {q.name}{q.isPaused ? ' (paused)' : ''}
                        </option>
                      ))}
                    </select>
                }
              </div>
            )}

            {/* Executor description banner */}
            {selectedQueue && executorDef && (
              <p style={s.executorDesc}>{executorDef.description}</p>
            )}

            {/* ── Payload section ── */}
            {selectedQueue && (
              <div style={s.section}>
                <div style={s.sectionHeader}>
                  <span style={s.sectionTitle}>Payload</span>
                  {isExecutorQueue
                    ? <span style={s.sectionHint}>Fill in the fields — hover Schema for reference</span>
                    : <span style={s.sectionHint}>No predefined schema — edit JSON directly</span>
                  }
                </div>

                {isExecutorQueue
                  ? <div style={s.fieldList}>{executorDef!.fields.map(renderField)}</div>
                  : <>
                      <textarea value={rawJson} spellCheck={false} rows={9}
                        onChange={e => { setRawJson(e.target.value); setJsonError(null) }}
                        style={{ ...s.input, fontFamily: 'var(--mono)', fontSize: 12,
                                 resize: 'vertical', border: jsonError ? '1px solid var(--danger)' : undefined }} />
                      {jsonError && <p style={s.errorText}>{jsonError}</p>}
                    </>
                }
              </div>
            )}

            {/* ── Options ── */}
            {selectedQueue && (
              <div style={s.section}>
                <div style={s.sectionHeader}>
                  <span style={s.sectionTitle}>Options</span>
                </div>
                <div style={s.optionsRow}>
                  <div style={s.fieldGroup}>
                    <label style={s.fieldLabel}>Priority <span style={s.schemaTypePill}>0–100</span></label>
                    <input type="number" min={0} max={100} value={priority}
                      onChange={e => setPriority(Number(e.target.value))}
                      style={{ ...s.input, width: 80 }} />
                    <p style={s.fieldHint}>Higher = picked up first</p>
                  </div>
                  <div style={s.fieldGroup}>
                    <label style={s.fieldLabel}>Max Attempts <span style={s.schemaTypePill}>1–20</span></label>
                    <input type="number" min={1} max={20} value={maxAttempts}
                      onChange={e => setMaxAttempts(Number(e.target.value))}
                      style={{ ...s.input, width: 80 }} />
                    <p style={s.fieldHint}>Retries on failure</p>
                  </div>
                </div>
              </div>
            )}

            {submitError && <p style={{ ...s.errorText, marginTop: 8 }}>{submitError}</p>}
          </div>

          {/* ── Footer ── */}
          <div style={s.modalFooter}>
            <button type="button" className="btn-ghost" onClick={onClose}>Cancel</button>
            <button type="submit" className="btn-primary" disabled={submitting || !selectedQueue}>
              {submitting ? 'Enqueueing…' : 'Enqueue Job'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ── Styles ────────────────────────────────────────────────────────────────────

const s: Record<string, React.CSSProperties> = {
  overlay: {
    position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.65)',
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    zIndex: 1000, padding: 16,
  },
  modal: {
    background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: 14,
    width: '100%', maxWidth: 580, maxHeight: '92vh',
    display: 'flex', flexDirection: 'column', boxShadow: 'var(--shadow)',
    overflow: 'hidden',
  },
  modalHeader: {
    display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start',
    padding: '18px 24px 12px', flexShrink: 0,
    borderBottom: '1px solid var(--border)',
  },
  modalTitle:    { fontSize: 16, fontWeight: 700, marginBottom: 2 },
  modalSubtitle: { fontSize: 12, color: 'var(--text-muted)', margin: 0 },
  closeBtn: {
    background: 'transparent', border: 'none', color: 'var(--text-muted)',
    fontSize: 16, cursor: 'pointer', padding: '4px 6px', borderRadius: 6,
  },
  schemaBtn: {
    background: 'rgba(99,102,241,0.12)', border: '1px solid rgba(99,102,241,0.3)',
    color: 'var(--accent)', borderRadius: 6, padding: '5px 10px',
    fontSize: 12, cursor: 'pointer', fontWeight: 600,
  },
  executorBadge: {
    marginLeft: 8, background: 'rgba(99,102,241,0.15)', color: 'var(--accent)',
    borderRadius: 10, padding: '1px 7px', fontSize: 11, fontWeight: 600,
  },

  // Schema panel
  schemaPanel: {
    background: 'var(--bg)', borderBottom: '1px solid var(--border)',
    padding: '14px 24px', flexShrink: 0,
  },
  schemaPanelHeader: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 },
  schemaPanelTitle:  { fontSize: 12, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' },
  schemaCloseBtn:    { background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: 13 },
  schemaTable:       { width: '100%', borderCollapse: 'collapse', fontSize: 12 },
  schemaTh: {
    textAlign: 'left', padding: '4px 8px', color: 'var(--text-muted)',
    fontSize: 11, fontWeight: 600, textTransform: 'uppercase',
    borderBottom: '1px solid var(--border)',
  },
  schemaTd:      { padding: '5px 8px', borderBottom: '1px solid var(--border)', fontSize: 12 },
  schemaExample: {
    fontFamily: 'var(--mono)', fontSize: 11, background: 'var(--bg-card)',
    padding: '8px 10px', borderRadius: 6, border: '1px solid var(--border)',
    margin: 0, whiteSpace: 'pre', overflowX: 'auto',
  },

  modalBody: {
    padding: '14px 24px', overflowY: 'auto', flex: 1,
    display: 'flex', flexDirection: 'column',
  },
  modalFooter: {
    display: 'flex', justifyContent: 'flex-end', gap: 8,
    padding: '12px 24px', borderTop: '1px solid var(--border)', flexShrink: 0,
  },

  fieldGroup:    { marginBottom: 14 },
  fieldList:     { display: 'flex', flexDirection: 'column' },
  fieldLabel: {
    display: 'flex', alignItems: 'center', gap: 6,
    fontSize: 12, fontWeight: 600, color: 'var(--text-muted)',
    marginBottom: 5, textTransform: 'uppercase', letterSpacing: '0.04em',
  },
  schemaTypePill: {
    fontSize: 10, background: 'var(--bg-hover)', color: 'var(--text-dim)',
    borderRadius: 4, padding: '1px 5px', fontWeight: 400,
    textTransform: 'none', letterSpacing: 0,
  },
  fieldHint:  { fontSize: 11, color: 'var(--text-dim)', marginTop: 4 },
  input: {
    width: '100%', padding: '8px 10px',
    background: 'var(--bg)', color: 'var(--text)',
    border: '1px solid var(--border)', borderRadius: 7,
    fontSize: 13, boxSizing: 'border-box' as const, outline: 'none',
  },
  select: {
    width: '100%', padding: '8px 10px',
    background: 'var(--bg)', color: 'var(--text)',
    border: '1px solid var(--border)', borderRadius: 7,
    fontSize: 13, cursor: 'pointer',
  },
  toggleLabel: { display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' },
  toggleTrack: {
    width: 44, height: 24, borderRadius: 12, position: 'relative',
    transition: 'background 0.2s', display: 'inline-block', flexShrink: 0,
  },
  toggleThumb: {
    position: 'absolute', top: 2, width: 20, height: 20,
    borderRadius: '50%', background: '#fff', transition: 'transform 0.2s',
  },
  section: { marginTop: 2, paddingTop: 14, borderTop: '1px solid var(--border)' },
  sectionHeader: { display: 'flex', alignItems: 'baseline', gap: 8, marginBottom: 12 },
  sectionTitle:  { fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' },
  sectionHint:   { fontSize: 11, color: 'var(--text-dim)' },
  optionsRow:    { display: 'flex', gap: 24 },
  executorDesc: {
    fontSize: 12, color: 'var(--text-muted)', fontStyle: 'italic',
    margin: '4px 0 12px', padding: '6px 10px',
    background: 'rgba(99,102,241,0.06)', borderRadius: 6,
    borderLeft: '3px solid var(--accent)',
  },
  errorText: { color: 'var(--danger)', fontSize: 12 },
  loading:   { fontSize: 13, color: 'var(--text-muted)', margin: 0 },
}
