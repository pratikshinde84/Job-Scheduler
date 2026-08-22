import { useState, useEffect, useCallback } from 'react'
import { notificationsApi, type NotificationItem } from '../api/notifications'

interface Props {
  onClose: () => void
  onRead: () => void   // called when messages marked read — parent refreshes unread count
}

const CHANNEL_EMOJI: Record<string, string> = {
  'push':   '🔔',
  'sms':    '💬',
  'in-app': '📩',
}

export default function InboxPanel({ onClose, onRead }: Props) {
  const [items, setItems]       = useState<NotificationItem[]>([])
  const [loading, setLoading]   = useState(true)
  const [error, setError]       = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      setItems(await notificationsApi.list())
      setError(null)
    } catch {
      setError('Failed to load notifications.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [load])

  const markAllRead = async () => {
    await notificationsApi.markAllRead()
    setItems(prev => prev.map(n => ({ ...n, isRead: true })))
    onRead()
  }

  const markOneRead = async (id: string) => {
    await notificationsApi.markOneRead(id)
    setItems(prev => prev.map(n => n.id === id ? { ...n, isRead: true } : n))
    onRead()
  }

  const unread = items.filter(n => !n.isRead).length

  return (
    <>
      {/* Backdrop */}
      <div style={s.backdrop} onClick={onClose} />

      {/* Panel */}
      <div style={s.panel}>
        {/* Header */}
        <div style={s.header}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontSize: 16 }}>📩</span>
            <h2 style={s.title}>Inbox</h2>
            {unread > 0 && (
              <span style={s.badge}>{unread}</span>
            )}
          </div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            {unread > 0 && (
              <button style={s.markAllBtn} onClick={markAllRead}>
                Mark all read
              </button>
            )}
            <button style={s.closeBtn} onClick={onClose} aria-label="Close inbox">✕</button>
          </div>
        </div>

        {/* Body */}
        <div style={s.body}>
          {loading && (
            <div style={s.center}><div className="spinner" /></div>
          )}
          {error && (
            <div style={{ padding: 16, color: 'var(--danger)', fontSize: 13 }}>{error}</div>
          )}
          {!loading && !error && items.length === 0 && (
            <div style={s.empty}>
              <div style={{ fontSize: 36, marginBottom: 8 }}>📭</div>
              <p style={{ color: 'var(--text-muted)', fontSize: 13 }}>No notifications yet.</p>
            </div>
          )}
          {!loading && items.map(n => (
            <div
              key={n.id}
              style={{ ...s.item, ...(n.isRead ? s.itemRead : s.itemUnread) }}
              onClick={() => { if (!n.isRead) markOneRead(n.id) }}
            >
              <div style={s.itemLeft}>
                <span style={s.channelEmoji}>
                  {CHANNEL_EMOJI[n.channel] ?? '🔔'}
                </span>
                {!n.isRead && <span style={s.unreadDot} />}
              </div>
              <div style={s.itemBody}>
                <div style={s.itemTitle}>{n.title}</div>
                <div style={s.itemMessage}>{n.message}</div>
                <div style={s.itemMeta}>
                  <span style={s.channelTag}>{n.channel}</span>
                  <span style={s.itemTime}>
                    {new Date(n.createdAt).toLocaleString()}
                  </span>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </>
  )
}

const s: Record<string, React.CSSProperties> = {
  backdrop: {
    position: 'fixed', inset: 0,
    background: 'rgba(0,0,0,0.3)',
    zIndex: 900,
  },
  panel: {
    position: 'fixed', top: 0, right: 0, bottom: 0,
    width: 380,
    background: 'var(--bg-card)',
    borderLeft: '1px solid var(--border)',
    display: 'flex', flexDirection: 'column',
    zIndex: 901,
    boxShadow: '-4px 0 24px rgba(0,0,0,0.2)',
  },
  header: {
    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
    padding: '16px 20px',
    borderBottom: '1px solid var(--border)',
    flexShrink: 0,
  },
  title: { fontSize: 15, fontWeight: 700, margin: 0 },
  badge: {
    background: 'var(--accent)',
    color: '#fff',
    borderRadius: 10,
    padding: '1px 7px',
    fontSize: 11,
    fontWeight: 700,
  },
  markAllBtn: {
    background: 'transparent',
    border: '1px solid var(--border)',
    color: 'var(--text-muted)',
    borderRadius: 6,
    padding: '4px 10px',
    fontSize: 11,
    cursor: 'pointer',
  },
  closeBtn: {
    background: 'transparent', border: 'none',
    color: 'var(--text-muted)', fontSize: 16,
    cursor: 'pointer', padding: '4px 6px', borderRadius: 6,
  },
  body: {
    flex: 1, overflowY: 'auto',
  },
  center: {
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    padding: 40,
  },
  empty: {
    display: 'flex', flexDirection: 'column', alignItems: 'center',
    justifyContent: 'center', padding: '60px 20px', textAlign: 'center',
  },
  item: {
    display: 'flex', gap: 12,
    padding: '14px 20px',
    borderBottom: '1px solid var(--border)',
    cursor: 'pointer',
    transition: 'background 0.1s',
  },
  itemUnread: {
    background: 'rgba(99,102,241,0.05)',
  },
  itemRead: {
    background: 'transparent',
    opacity: 0.7,
  },
  itemLeft: {
    position: 'relative',
    flexShrink: 0,
    display: 'flex', alignItems: 'flex-start', justifyContent: 'center',
    width: 32,
  },
  channelEmoji: { fontSize: 20, lineHeight: 1 },
  unreadDot: {
    position: 'absolute', top: 0, right: 0,
    width: 8, height: 8, borderRadius: '50%',
    background: 'var(--accent)',
  },
  itemBody: { flex: 1, minWidth: 0 },
  itemTitle: {
    fontSize: 13, fontWeight: 600, color: 'var(--text)',
    marginBottom: 3,
  },
  itemMessage: {
    fontSize: 13, color: 'var(--text-muted)',
    lineHeight: 1.4,
    marginBottom: 6,
  },
  itemMeta: {
    display: 'flex', gap: 8, alignItems: 'center',
  },
  channelTag: {
    fontSize: 10, background: 'var(--bg-hover)',
    color: 'var(--text-dim)', borderRadius: 4,
    padding: '1px 5px', textTransform: 'uppercase',
  },
  itemTime: { fontSize: 11, color: 'var(--text-dim)' },
}
