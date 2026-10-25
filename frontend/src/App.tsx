import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './components/AppShell'
import { Spinner } from './components/ui/bits'
import { useAuth } from './lib/auth'
import { AcceptInvitePage } from './pages/AcceptInvitePage'
import { AuthPage } from './pages/AuthPage'
import { BoardPage } from './pages/BoardPage'
import { InboxPage } from './pages/InboxPage'
import { ListPage } from './pages/ListPage'
import { MyTasksPage } from './pages/MyTasksPage'
import { OverviewPage } from './pages/OverviewPage'
import { ProjectLayout } from './pages/ProjectLayout'
import { ProjectOverviewPage } from './pages/ProjectOverviewPage'
import { ProjectsPage } from './pages/ProjectsPage'
import { RootRedirect } from './pages/RootRedirect'
import { SettingsPage } from './pages/SettingsPage'
import { TeamPage } from './pages/TeamPage'
import { TimelinePage } from './pages/TimelinePage'

function Protected({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth()
  if (loading) return <div className="grid h-full place-items-center"><Spinner /></div>
  if (!user) return <Navigate to="/login" replace />
  return <>{children}</>
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<AuthPage mode="login" />} />
      <Route path="/register" element={<AuthPage mode="register" />} />
      <Route path="/invite/:token" element={<AcceptInvitePage />} />
      <Route path="/" element={<Protected><RootRedirect /></Protected>} />
      <Route path="/w/:wsId" element={<Protected><AppShell /></Protected>}>
        <Route index element={<OverviewPage />} />
        <Route path="my-tasks" element={<MyTasksPage />} />
        <Route path="projects" element={<ProjectsPage />} />
        <Route path="team" element={<TeamPage />} />
        <Route path="inbox" element={<InboxPage />} />
        <Route path="settings" element={<SettingsPage />} />
        <Route path="p/:projectId" element={<ProjectLayout />}>
          <Route index element={<ProjectOverviewPage />} />
          <Route path="board" element={<BoardPage />} />
          <Route path="list" element={<ListPage />} />
          <Route path="timeline" element={<TimelinePage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
