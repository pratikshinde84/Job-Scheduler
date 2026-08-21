import api from '../lib/axios'
import type { Project } from '../types'

export const projectsApi = {
  list: () =>
    api.get<Project[]>('/projects').then((r) => r.data),

  get: (id: string) =>
    api.get<Project>(`/projects/${id}`).then((r) => r.data),

  create: (name: string) =>
    api.post<Project>('/projects', { name }).then((r) => r.data),

  rename: (id: string, name: string) =>
    api.put<Project>(`/projects/${id}`, { name }).then((r) => r.data),

  delete: (id: string) =>
    api.delete(`/projects/${id}`),
}
