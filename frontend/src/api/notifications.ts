import api from '../lib/axios'

export interface NotificationItem {
  id: string
  title: string
  message: string
  channel: string
  isRead: boolean
  createdAt: string
}

export const notificationsApi = {
  list: () =>
    api.get<NotificationItem[]>('/notifications').then(r => r.data),

  unreadCount: () =>
    api.get<{ count: number }>('/notifications/unread-count').then(r => r.data.count),

  markAllRead: () =>
    api.post('/notifications/mark-all-read'),

  markOneRead: (id: string) =>
    api.post(`/notifications/${id}/mark-read`),
}
