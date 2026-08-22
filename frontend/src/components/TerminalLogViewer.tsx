import React, { useState, useEffect, useRef, useCallback } from 'react'
import { logsApi, type LogEntry, type ExecutionMetrics } from '../api/logs'

export default function TerminalLogViewer() {
  const [logs, setLogs]             = useState<LogEntry[]>([])
  const [metrics, setMetrics]       = useState<ExecutionMetrics | null>(null)
  const [loading, setLoading]       = useState(true)
  const [error, setError]           = useState<string | null>(null)

  // Filters & Controls
  const [search, setSearch]         = useState('')
  const [levelFilter, setLevelFilter] = useState<'ALL' | 'INFO' | 'SUCCESS' | 'WARN' | 'ERROR'>('ALL')
  const [autoScroll, setAutoScroll] = useState(true)
  const [pollInterval, setPollInterval] = useState<number>(3000) // ms, 0 = paused
  const [expandedLogId, setExpandedLogId] = useState<string | null>(null)
  const [commandInput, setCommandInput]   = useState('')
  const [commandHistory, setCommandHistory] = useState<string[]>([])

  const terminalBodyRef = useRef<HTMLDivElement>(null)

  // Fetch Logs
  const fetchLogs = useCallback(async () => {
    try {
      const res = await logsApi.getLogs(60)
      setLogs(res.logs)
      setMetrics(res.metrics)
      setError(null)
    } catch (err: unknown) {
      setError('Failed to connect to execution log stream.')
    } finally {
      setLoading(false)
    }
  }, [])

  // Polling setup
  useEffect(() => {
    fetchLogs()
    if (pollInterval <= 0) return

    const interval = setInterval(() => {
      fetchLogs()
    }, pollInterval)

    return () => clearInterval(interval)
  }, [fetchLogs, pollInterval])

  // Auto scroll to bottom when logs update
  useEffect(() => {
    if (autoScroll && terminalBodyRef.current) {
      terminalBodyRef.current.scrollTop = terminalBodyRef.current.scrollHeight
    }
  }, [logs, autoScroll])

  // Command Prompt handler
  const handleCommandSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const cmd = commandInput.trim()
    if (!cmd) return

    const parts = cmd.toLowerCase().split(' ')
    const mainCmd = parts[0]

    setCommandHistory(prev => [...prev, `$ ${cmd}`])

    if (mainCmd === 'clear') {
      setLogs([])
      setCommandHistory([])
    } else if (mainCmd === 'help') {
      setCommandHistory(prev => [
        ...prev,
        'Available terminal commands:',
        '  clear            - Clear terminal output screen',
        '  stats            - Output current execution metrics',
        '  filter <level>   - Set level filter (all, info, success, warn, error)',
        '  search <term>    - Search log output by keyword',
        '  pause / resume   - Toggle live polling stream',
        '  help             - Show this help menu',
      ])
    } else if (mainCmd === 'stats' && metrics) {
      setCommandHistory(prev => [
        ...prev,
        `[METRICS] Total Jobs: ${metrics.totalJobs} | Total Attempts: ${metrics.totalAttempts}`,
        `[METRICS] Success Rate: ${metrics.successRatePct}% | Retry/Failure Rate: ${metrics.retryRatePct}%`,
        `[METRICS] Avg Execution: ${metrics.avgDurationMs}ms | Active Workers: ${metrics.activeWorkerCount}`,
      ])
    } else if (mainCmd === 'filter' && parts[1]) {
      const lvl = parts[1].toUpperCase()
      if (['ALL', 'INFO', 'SUCCESS', 'WARN', 'ERROR'].includes(lvl)) {
        setLevelFilter(lvl as any)
        setCommandHistory(prev => [...prev, `[SYSTEM] Level filter set to: ${lvl}`])
      } else {
        setCommandHistory(prev => [...prev, `[ERROR] Invalid level. Use ALL, INFO, SUCCESS, WARN, ERROR`])
      }
    } else if (mainCmd === 'search') {
      const term = parts.slice(1).join(' ')
      setSearch(term)
      setCommandHistory(prev => [...prev, `[SYSTEM] Search filter set to "${term}"`])
    } else if (mainCmd === 'pause') {
      setPollInterval(0)
      setCommandHistory(prev => [...prev, '[SYSTEM] Live log streaming paused.'])
    } else if (mainCmd === 'resume') {
      setPollInterval(3000)
      setCommandHistory(prev => [...prev, '[SYSTEM] Live log streaming resumed (3s).'])
    } else {
      setCommandHistory(prev => [...prev, `command not recognized: ${mainCmd}. Type 'help' for commands.`])
    }

    setCommandInput('')
  }

  // Filtering
  const filteredLogs = logs.filter(log => {
    if (levelFilter !== 'ALL' && log.level !== levelFilter) return false
    if (search) {
      const query = search.toLowerCase()
      const matchText = `${log.jobId} ${log.queueName} ${log.workerName} ${log.message} ${log.event} ${log.details || ''}`.toLowerCase()
      if (!matchText.includes(query)) return false
    }
    return true
  })

  // Format helper for timestamps
  const formatTime = (isoString: string) => {
    try {
      const d = new Date(isoString)
      return d.toTimeString().split(' ')[0] + '.' + String(d.getMilliseconds()).padStart(3, '0')
    } catch {
      return isoString
    }
  }

  // Level color map
  const getLevelStyle = (level: string) => {
    switch (level) {
      case 'SUCCESS': return { color: '#4ade80', bg: 'rgba(74,222,128,0.15)', border: '#22c55e' }
      case 'WARN':    return { color: '#fbbf24', bg: 'rgba(251,191,36,0.15)', border: '#f59e0b' }
      case 'ERROR':   return { color: '#f87171', bg: 'rgba(248,113,113,0.15)', border: '#ef4444' }
      case 'INFO':
      default:        return { color: '#38bdf8', bg: 'rgba(56,189,248,0.15)', border: '#0284c7' }
    }
  }

  return (
    <div style={styles.container}>
      {/* ── Terminal Window Header ── */}
      <div style={styles.header}>
        <div style={styles.windowDots}>
          <span style={{ ...styles.dot, background: '#ff5f56' }} title="Close" />
          <span style={{ ...styles.dot, background: '#ffbd2e' }} title="Minimize" />
          <span style={{ ...styles.dot, background: '#27c93f' }} title="Maximize" />
        </div>
        <div style={styles.windowTitle}>
          <span>jobscheduler@worker-daemon: ~/execution-logs</span>
          {pollInterval > 0 && <span style={styles.livePulse}>● LIVE</span>}
        </div>
        <div style={styles.headerActions}>
          <button
            style={styles.headerBtn}
            onClick={() => setPollInterval(prev => prev > 0 ? 0 : 3000)}
          >
            {pollInterval > 0 ? '⏸ Pause' : '▶ Resume'}
          </button>
          <button style={styles.headerBtn} onClick={fetchLogs}>
            🔄 Refresh
          </button>
          <button style={styles.headerBtn} onClick={() => setLogs([])}>
            🧹 Clear
          </button>
        </div>
      </div>

      {/* ── Real-time Execution Metrics Toolbar ── */}
      {metrics && (
        <div style={styles.metricsBar}>
          <div style={styles.metricItem}>
            <span style={styles.metricLabel}>⚡ EXECUTIONS:</span>
            <span style={styles.metricVal}>{metrics.totalAttempts} attempts ({metrics.totalJobs} jobs)</span>
          </div>
          <div style={styles.metricItem}>
            <span style={styles.metricLabel}>✅ SUCCESS RATE:</span>
            <span style={{ ...styles.metricVal, color: '#4ade80' }}>{metrics.successRatePct}%</span>
          </div>
          <div style={styles.metricItem}>
            <span style={styles.metricLabel}>⏱️ AVG DURATION:</span>
            <span style={{ ...styles.metricVal, color: '#38bdf8' }}>{metrics.avgDurationMs} ms</span>
          </div>
          <div style={styles.metricItem}>
            <span style={styles.metricLabel}>🔄 RETRY RATE:</span>
            <span style={{ ...styles.metricVal, color: metrics.retryRatePct > 0 ? '#fbbf24' : 'var(--text-muted)' }}>
              {metrics.retryRatePct}%
            </span>
          </div>
          <div style={styles.metricItem}>
            <span style={styles.metricLabel}>🤖 ACTIVE WORKERS:</span>
            <span style={{ ...styles.metricVal, color: '#a78bfa' }}>{metrics.activeWorkerCount} polling</span>
          </div>
        </div>
      )}

      {/* ── Filter & Search Toolbar ── */}
      <div style={styles.toolbar}>
        <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
          <div style={{ position: 'relative' }}>
            <input
              type="text"
              placeholder="🔍 Search logs by queue, worker, job ID..."
              value={search}
              onChange={e => setSearch(e.target.value)}
              style={styles.searchInput}
            />
            {search && (
              <button style={styles.clearSearchBtn} onClick={() => setSearch('')}>✕</button>
            )}
          </div>

          <select
            value={levelFilter}
            onChange={e => setLevelFilter(e.target.value as any)}
            style={styles.selectFilter}
          >
            <option value="ALL">All Levels</option>
            <option value="INFO">INFO</option>
            <option value="SUCCESS">SUCCESS</option>
            <option value="WARN">WARN</option>
            <option value="ERROR">ERROR</option>
          </select>

          <label style={styles.toggleLabel}>
            <input
              type="checkbox"
              checked={autoScroll}
              onChange={e => setAutoScroll(e.target.checked)}
              style={{ accentColor: 'var(--accent)' }}
            />
            Auto-scroll
          </label>
        </div>

        <div style={{ fontSize: 11, color: '#94a3b8' }}>
          Showing <strong>{filteredLogs.length}</strong> of {logs.length} logs
        </div>
      </div>

      {/* ── Terminal Console Output ── */}
      <div style={styles.terminalBody} ref={terminalBodyRef}>
        {loading && logs.length === 0 && (
          <div style={styles.loadingText}>Initializing execution stream connection...</div>
        )}

        {error && (
          <div style={styles.errorBanner}>{error}</div>
        )}

        {!loading && filteredLogs.length === 0 && (
          <div style={styles.emptyText}>
            No execution logs match the current filters. Enqueue jobs or adjust search filters.
          </div>
        )}

        {/* Command history output */}
        {commandHistory.map((line, idx) => (
          <div key={`cmd-${idx}`} style={styles.cmdHistoryLine}>
            {line}
          </div>
        ))}

        {/* Render Log Lines (Sorted ascending: oldest at top, latest at bottom) */}
        {[...filteredLogs]
          .sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime())
          .map(log => {
            const isExpanded = expandedLogId === log.id
            const lvlStyle = getLevelStyle(log.level)


          return (
            <div
              key={log.id}
              style={{
                ...styles.logRow,
                background: isExpanded ? 'rgba(30, 41, 59, 0.6)' : 'transparent',
              }}
              onClick={() => setExpandedLogId(isExpanded ? null : log.id)}
            >
              <div style={styles.logLine}>
                {/* Timestamp */}
                <span style={styles.timestamp}>[{formatTime(log.timestamp)}]</span>

                {/* Level Badge */}
                <span style={{
                  ...styles.levelBadge,
                  color: lvlStyle.color,
                  background: lvlStyle.bg,
                }}>
                  {log.level}
                </span>

                {/* Event Name */}
                <span style={styles.eventBadge}>{log.event}</span>

                {/* Worker Assignment */}
                <span style={styles.workerBadge}>
                  👷 {log.workerName}
                </span>

                {/* Queue Name */}
                <span style={styles.queueBadge}>
                  ⚙ {log.queueName}
                </span>

                {/* Job Short ID */}
                <span style={styles.jobIdBadge}>
                  #{log.jobId.slice(0, 8)}
                </span>

                {/* Duration if available */}
                {log.durationMs != null && (
                  <span style={styles.durationBadge}>
                    ⏱ {log.durationMs}ms
                  </span>
                )}

                {/* Main Message */}
                <span style={styles.logMessage}>
                  {log.message}
                </span>

                {/* Expand Indicator */}
                <span style={styles.expandIcon}>
                  {isExpanded ? '▼' : '▶'}
                </span>
              </div>

              {/* Expanded Detail Drawer */}
              {isExpanded && (
                <div style={styles.detailDrawer} onClick={e => e.stopPropagation()}>
                  <div style={styles.detailGrid}>
                    <div>
                      <strong>Job ID:</strong> <code>{log.jobId}</code>
                    </div>
                    <div>
                      <strong>Project:</strong> {log.projectName}
                    </div>
                    <div>
                      <strong>Attempt:</strong> {log.attemptNumber} of {log.maxAttempts}
                    </div>
                    <div>
                      <strong>Assigned Worker:</strong> {log.workerName}
                    </div>
                  </div>

                  {/* Payload if present */}
                  {log.payload && (
                    <div style={styles.detailSection}>
                      <span style={styles.detailTitle}>📦 Input Payload:</span>
                      <pre style={styles.codeBlock}>{JSON.stringify(log.payload, null, 2)}</pre>
                    </div>
                  )}

                  {/* Result if present */}
                  {log.result && (
                    <div style={styles.detailSection}>
                      <span style={styles.detailTitle}>🎉 Execution Output / Result:</span>
                      <pre style={styles.codeBlock}>{JSON.stringify(log.result, null, 2)}</pre>
                    </div>
                  )}

                  {/* Error details if present */}
                  {log.details && (
                    <div style={styles.detailSection}>
                      <span style={{ ...styles.detailTitle, color: '#f87171' }}>💥 Error Details / Stack Trace:</span>
                      <pre style={{ ...styles.codeBlock, color: '#fca5a5', borderColor: 'rgba(239,68,68,0.3)' }}>
                        {log.details}
                      </pre>
                    </div>
                  )}
                </div>
              )}
            </div>
          )
        })}
      </div>

      {/* ── Interactive Command Line Prompt ── */}
      <form onSubmit={handleCommandSubmit} style={styles.promptBar}>
        <span style={styles.promptSymbol}>$</span>
        <input
          type="text"
          value={commandInput}
          onChange={e => setCommandInput(e.target.value)}
          placeholder="Type terminal command (e.g. 'help', 'stats', 'clear', 'filter error')..."
          style={styles.promptInput}
        />
        <button type="submit" style={styles.promptBtn}>Run</button>
      </form>
    </div>
  )
}

// ── Retro Monospaced Terminal Styling ────────────────────────────────────────

const styles: Record<string, React.CSSProperties> = {
  container: {
    background: '#090d16',
    border: '1px solid #1e293b',
    borderRadius: 12,
    boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.5), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
    marginTop: 24,
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace',
  },
  header: {
    background: '#0f172a',
    borderBottom: '1px solid #1e293b',
    padding: '10px 16px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  windowDots: {
    display: 'flex',
    gap: 8,
    alignItems: 'center',
  },
  dot: {
    width: 12,
    height: 12,
    borderRadius: '50%',
    display: 'inline-block',
  },
  windowTitle: {
    fontSize: 12,
    fontWeight: 600,
    color: '#94a3b8',
    display: 'flex',
    alignItems: 'center',
    gap: 8,
  },
  livePulse: {
    fontSize: 10,
    color: '#4ade80',
    background: 'rgba(74,222,128,0.15)',
    padding: '2px 6px',
    borderRadius: 4,
    fontWeight: 700,
  },
  headerActions: {
    display: 'flex',
    gap: 8,
  },
  headerBtn: {
    background: '#1e293b',
    border: '1px solid #334155',
    color: '#cbd5e1',
    fontSize: 11,
    padding: '4px 10px',
    borderRadius: 6,
    cursor: 'pointer',
    fontFamily: 'inherit',
  },
  metricsBar: {
    background: '#0d1322',
    borderBottom: '1px solid #1e293b',
    padding: '8px 16px',
    display: 'flex',
    gap: 20,
    flexWrap: 'wrap',
    fontSize: 11,
  },
  metricItem: {
    display: 'flex',
    gap: 6,
    alignItems: 'center',
  },
  metricLabel: {
    color: '#64748b',
    fontWeight: 600,
  },
  metricVal: {
    fontWeight: 700,
    color: '#e2e8f0',
  },
  toolbar: {
    background: '#0b101d',
    borderBottom: '1px solid #1e293b',
    padding: '8px 16px',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: 12,
    flexWrap: 'wrap',
  },
  searchInput: {
    background: '#0f172a',
    border: '1px solid #334155',
    color: '#f8fafc',
    fontSize: 11,
    padding: '5px 28px 5px 10px',
    borderRadius: 6,
    width: 260,
    outline: 'none',
    fontFamily: 'inherit',
  },
  clearSearchBtn: {
    position: 'absolute',
    right: 6,
    top: 5,
    background: 'transparent',
    border: 'none',
    color: '#64748b',
    cursor: 'pointer',
    fontSize: 11,
  },
  selectFilter: {
    background: '#0f172a',
    border: '1px solid #334155',
    color: '#f8fafc',
    fontSize: 11,
    padding: '5px 10px',
    borderRadius: 6,
    outline: 'none',
    cursor: 'pointer',
    fontFamily: 'inherit',
  },
  toggleLabel: {
    fontSize: 11,
    color: '#94a3b8',
    display: 'flex',
    alignItems: 'center',
    gap: 6,
    cursor: 'pointer',
  },
  terminalBody: {
    height: 380,
    overflowY: 'auto',
    padding: '12px 16px',
    fontSize: 12,
    lineHeight: 1.6,
    color: '#cbd5e1',
    display: 'flex',
    flexDirection: 'column',
    gap: 2,
  },
  loadingText: {
    color: '#38bdf8',
    padding: '20px 0',
    textAlign: 'center',
  },
  errorBanner: {
    background: 'rgba(239,68,68,0.15)',
    border: '1px solid #ef4444',
    color: '#fca5a5',
    padding: '8px 12px',
    borderRadius: 6,
    marginBottom: 8,
  },
  emptyText: {
    color: '#64748b',
    textAlign: 'center',
    padding: '40px 0',
    fontStyle: 'italic',
  },
  cmdHistoryLine: {
    color: '#94a3b8',
    fontSize: 11,
    padding: '2px 0',
  },
  logRow: {
    borderRadius: 6,
    padding: '3px 6px',
    cursor: 'pointer',
    transition: 'background 0.15s',
  },
  logLine: {
    display: 'flex',
    alignItems: 'baseline',
    gap: 8,
    flexWrap: 'nowrap',
    overflowX: 'hidden',
    whiteSpace: 'nowrap',
  },
  timestamp: {
    color: '#64748b',
    fontSize: 11,
    flexShrink: 0,
  },
  levelBadge: {
    fontSize: 10,
    fontWeight: 700,
    padding: '1px 6px',
    borderRadius: 4,
    flexShrink: 0,
  },
  eventBadge: {
    color: '#e2e8f0',
    fontSize: 10,
    fontWeight: 600,
    background: '#1e293b',
    padding: '1px 6px',
    borderRadius: 4,
    flexShrink: 0,
  },
  workerBadge: {
    color: '#a78bfa',
    fontSize: 11,
    flexShrink: 0,
  },
  queueBadge: {
    color: '#38bdf8',
    fontSize: 11,
    flexShrink: 0,
  },
  jobIdBadge: {
    color: '#94a3b8',
    fontSize: 11,
    flexShrink: 0,
  },
  durationBadge: {
    color: '#fbbf24',
    fontSize: 11,
    flexShrink: 0,
  },
  logMessage: {
    color: '#f1f5f9',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    flex: 1,
  },
  expandIcon: {
    fontSize: 9,
    color: '#64748b',
    flexShrink: 0,
    marginLeft: 4,
  },
  detailDrawer: {
    marginTop: 8,
    marginBottom: 8,
    padding: 12,
    background: '#0d1424',
    border: '1px solid #1e293b',
    borderRadius: 8,
    fontSize: 11,
    display: 'flex',
    flexDirection: 'column',
    gap: 8,
  },
  detailGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
    gap: 8,
    color: '#cbd5e1',
  },
  detailSection: {
    marginTop: 4,
  },
  detailTitle: {
    display: 'block',
    fontWeight: 700,
    color: '#94a3b8',
    marginBottom: 4,
  },
  codeBlock: {
    margin: 0,
    padding: '8px 12px',
    background: '#070a12',
    border: '1px solid #1e293b',
    borderRadius: 6,
    color: '#e2e8f0',
    fontSize: 11,
    overflowX: 'auto',
    whiteSpace: 'pre-wrap',
  },
  promptBar: {
    background: '#070a12',
    borderTop: '1px solid #1e293b',
    padding: '8px 16px',
    display: 'flex',
    alignItems: 'center',
    gap: 8,
  },
  promptSymbol: {
    color: '#4ade80',
    fontWeight: 700,
    fontSize: 14,
  },
  promptInput: {
    flex: 1,
    background: 'transparent',
    border: 'none',
    color: '#4ade80',
    fontSize: 12,
    outline: 'none',
    fontFamily: 'inherit',
  },
  promptBtn: {
    background: '#1e293b',
    border: '1px solid #334155',
    color: '#4ade80',
    fontSize: 11,
    padding: '3px 10px',
    borderRadius: 4,
    cursor: 'pointer',
    fontFamily: 'inherit',
  },
}
