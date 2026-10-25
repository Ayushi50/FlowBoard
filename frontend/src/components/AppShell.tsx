import { useEffect, useState } from 'react'
import { Navigate, Outlet, useParams } from 'react-router-dom'
import { useWorkspaces } from '@/hooks/queries'
import { WorkspaceContext } from '@/lib/workspace'
import { Sidebar } from './Sidebar'
import { TaskPanel } from './TaskPanel'
import { Topbar } from './Topbar'
import { Spinner } from './ui/bits'

export function AppShell() {
  const { wsId = '' } = useParams()
  const { data: workspaces, isLoading } = useWorkspaces()
  const [collapsed, setCollapsed] = useState(() => {
    try {
      return localStorage.getItem('flowboard.sidebar') === 'collapsed'
    } catch {
      return false
    }
  })
  const [drawer, setDrawer] = useState(false)

  useEffect(() => {
    try {
      localStorage.setItem('flowboard.sidebar', collapsed ? 'collapsed' : 'open')
    } catch {
      /* ignore */
    }
  }, [collapsed])

  useEffect(() => {
    if (wsId) {
      try {
        localStorage.setItem('flowboard.workspace', wsId)
      } catch {
        /* ignore */
      }
    }
  }, [wsId])

  if (isLoading) return <div className="grid h-full place-items-center"><Spinner /></div>
  const workspace = workspaces?.find((w) => w.id === wsId)
  if (!workspace) return <Navigate to="/" replace />

  const ctx = {
    workspace,
    wsId,
    role: workspace.myRole,
    isAdmin: workspace.myRole === 'ADMIN',
    canCreateProject: workspace.myRole !== 'MEMBER',
  }

  return (
    <WorkspaceContext.Provider value={ctx}>
      <div className="flex h-full">
        {drawer && <div className="anim-fade fixed inset-0 z-30 bg-black/40 lg:hidden" onClick={() => setDrawer(false)} aria-hidden />}
        <Sidebar collapsed={collapsed} drawerOpen={drawer} onNavigate={() => setDrawer(false)} />
        <div className="flex min-w-0 flex-1 flex-col">
          <Topbar collapsed={collapsed} onToggleSidebar={() => setCollapsed((c) => !c)} onOpenDrawer={() => setDrawer(true)} />
          <main className="scroll-thin min-h-0 flex-1 overflow-auto">
            <Outlet />
          </main>
        </div>
      </div>
      <TaskPanel />
    </WorkspaceContext.Provider>
  )
}
