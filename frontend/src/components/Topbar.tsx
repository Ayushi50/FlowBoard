import { Bell, ChevronRight, Menu as MenuIcon, PanelLeft, Plus, Search, FolderPlus, SquarePen } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useProject, useUnreadCount } from '@/hooks/queries'
import { useAuth } from '@/lib/auth'
import { useWorkspace } from '@/lib/workspace'
import { ProjectDialog } from './ProjectDialog'
import { QuickTaskDialog } from './QuickTaskDialog'
import { SearchPalette } from './SearchPalette'
import { Avatar } from './ui/avatar'
import { Button } from './ui/button'
import { Menu, MenuContent, MenuItem, MenuTrigger } from './ui/dropdown'

const PAGE_NAMES: Record<string, string> = {
  'my-tasks': 'My Tasks', projects: 'Projects', team: 'Team', inbox: 'Inbox', settings: 'Settings',
  board: 'Board', list: 'List', timeline: 'Timeline',
}

function Breadcrumbs() {
  const { workspace, wsId } = useWorkspace()
  const { projectId } = useParams()
  const { data: project } = useProject(projectId ?? '')
  const { pathname } = useLocation()
  const parts = pathname.split('/').filter(Boolean).slice(2)
  const crumbs: { label: string; to?: string }[] = [{ label: workspace.name, to: `/w/${wsId}` }]
  if (parts.length === 0) crumbs.push({ label: 'Overview' })
  else if (parts[0] === 'p') {
    crumbs.push({ label: 'Projects', to: `/w/${wsId}/projects` })
    crumbs.push({ label: project?.name ?? '…', to: parts[2] ? `/w/${wsId}/p/${projectId}` : undefined })
    if (parts[2]) crumbs.push({ label: PAGE_NAMES[parts[2]] ?? parts[2] })
  } else crumbs.push({ label: PAGE_NAMES[parts[0]] ?? parts[0] })

  return (
    <nav aria-label="Breadcrumb" className="flex min-w-0 items-center gap-1 text-[13px] text-muted-foreground">
      {crumbs.map((c, i) => (
        <span key={i} className="flex min-w-0 items-center gap-1">
          {i > 0 && <ChevronRight size={12} className="shrink-0" />}
          {c.to && i < crumbs.length - 1 ? (
            <Link to={c.to} className="max-w-[140px] truncate hover:text-foreground">{c.label}</Link>
          ) : (
            <span className={i === crumbs.length - 1 ? 'max-w-[200px] truncate font-medium text-foreground' : 'max-w-[140px] truncate'}>{c.label}</span>
          )}
        </span>
      ))}
    </nav>
  )
}

export function Topbar({ collapsed: _collapsed, onToggleSidebar, onOpenDrawer }: { collapsed: boolean; onToggleSidebar: () => void; onOpenDrawer: () => void }) {
  const { wsId, canCreateProject } = useWorkspace()
  const { user, logout } = useAuth()
  const { data: unread } = useUnreadCount(wsId)
  const navigate = useNavigate()
  const [searchOpen, setSearchOpen] = useState(false)
  const [taskOpen, setTaskOpen] = useState(false)
  const [projectOpen, setProjectOpen] = useState(false)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setSearchOpen(true)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  return (
    <header className="flex h-12 shrink-0 items-center gap-2 border-b border-border bg-surface px-3">
      <Button variant="ghost" size="icon" className="lg:hidden" onClick={onOpenDrawer} aria-label="Open navigation">
        <MenuIcon size={18} />
      </Button>
      <Button variant="ghost" size="icon" className="hidden lg:inline-flex" onClick={onToggleSidebar} aria-label="Toggle sidebar">
        <PanelLeft size={16} />
      </Button>
      <div className="min-w-0 flex-1 overflow-hidden"><Breadcrumbs /></div>

      <button
        onClick={() => setSearchOpen(true)}
        className="flex h-8 w-9 items-center justify-center gap-2 rounded-md border border-border bg-surface-2 text-[13px] text-muted-foreground hover:border-muted-foreground/40 sm:w-56 sm:justify-start sm:px-2.5"
        aria-label="Search"
      >
        <Search size={14} />
        <span className="hidden flex-1 text-left sm:block">Search…</span>
        <kbd className="hidden rounded border border-border px-1 text-[10px] sm:block">⌘K</kbd>
      </button>

      <Menu>
        <MenuTrigger asChild>
          <Button variant="primary" size="md" aria-label="Create new"><Plus size={14} /><span className="hidden sm:inline">New</span></Button>
        </MenuTrigger>
        <MenuContent>
          <MenuItem onSelect={() => setTaskOpen(true)}><SquarePen size={14} /> New task</MenuItem>
          {canCreateProject && <MenuItem onSelect={() => setProjectOpen(true)}><FolderPlus size={14} /> New project</MenuItem>}
        </MenuContent>
      </Menu>

      <Button variant="ghost" size="icon" className="relative" onClick={() => navigate(`/w/${wsId}/inbox`)} aria-label={`Notifications${unread?.count ? `, ${unread.count} unread` : ''}`}>
        <Bell size={16} />
        {!!unread?.count && <span className="absolute right-1 top-1 h-2 w-2 rounded-full bg-danger ring-2 ring-surface" />}
      </Button>

      <Menu>
        <MenuTrigger aria-label="Profile menu" className="rounded-full"><Avatar user={user} size={28} /></MenuTrigger>
        <MenuContent>
          <MenuItem onSelect={() => navigate(`/w/${wsId}/settings`)}>Profile & settings</MenuItem>
          <MenuItem onSelect={() => { logout(); navigate('/login') }}>Log out</MenuItem>
        </MenuContent>
      </Menu>

      <SearchPalette open={searchOpen} onOpenChange={setSearchOpen} />
      <QuickTaskDialog open={taskOpen} onOpenChange={setTaskOpen} />
      <ProjectDialog open={projectOpen} onOpenChange={setProjectOpen} />
    </header>
  )
}
