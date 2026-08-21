import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'

import Layout           from './components/Layout'
import ProtectedRoute   from './components/ProtectedRoute'

import LoginPage        from './pages/LoginPage'
import AuthCallbackPage from './pages/AuthCallbackPage'
import DashboardPage    from './pages/DashboardPage'
import ProjectsPage     from './pages/ProjectsPage'
import QueuesPage       from './pages/QueuesPage'
import JobsPage         from './pages/JobsPage'

/**
 * Human-readable route structure:
 *
 *  /login                                          — public
 *  /auth/callback                                  — public
 *  /                                               → Dashboard
 *  /projects                                       → Projects list
 *  /projects/:projectName                          → Queues for that project
 *  /projects/:projectName/queues/:queueName/jobs   → Jobs for that queue
 *  /jobs                                           → All jobs (global view)
 */
export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* ── Public ──────────────────────────────────────────── */}
        <Route path="/login"         element={<LoginPage />} />
        <Route path="/auth/callback" element={<AuthCallbackPage />} />

        {/* ── Protected ───────────────────────────────────────── */}
        <Route
          element={
            <ProtectedRoute>
              <Layout />
            </ProtectedRoute>
          }
        >
          <Route index element={<DashboardPage />} />

          <Route path="projects" element={<ProjectsPage />} />

          {/* :projectName = slugified project name, e.g. "my-app" */}
          <Route path="projects/:projectName" element={<QueuesPage />} />

          {/* :queueName = queue name, e.g. "email-notifications" */}
          <Route
            path="projects/:projectName/queues/:queueName/jobs"
            element={<JobsPage />}
          />

          {/* Global all-jobs view */}
          <Route path="jobs" element={<JobsPage />} />

          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
