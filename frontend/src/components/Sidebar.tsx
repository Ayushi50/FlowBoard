import { Bell, Check, ChevronsUpDown, FolderKanban, Home, ListChecks, LogOut, Plus, Settings, Star, Users, Moon, Sun } from 'lucide-react'
import { useState } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useProjects, useRecentProjects, useUnreadCount, useWorkspaces } from '@/hooks/queries'
import { useAuth } from '@/lib/auth'
import { useTheme } from '@/lib/theme'
import { useWorkspace } from '@/lib/workspace'
import { cn } from '@/lib/utils'
import { ProjectDialog } from './ProjectDialog'
import { Avatar } from './ui/avatar'
import { Menu, MenuContent, MenuItem, MenuLabel, MenuSeparator, MenuTrigger } from './ui/dropdown'

const nav = [
  { to: '', label: 'Overview', icon: Home, end: true },
  { to: 'my-tasks', label: 'My Tasks', icon: ListChecks },
  { to: 'projects', label: 'Projects', icon: FolderKanban },
  { to: 'team', label: 'Team', icon: Users },
  { to: 'inbox', label: 'Inbox', icon: Bell },
  { to: 'settings', label: 'Settings', icon: Settings },
]

export function Logo({ size = 24 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden>
      <rect width="32" height="32" rx="8" fill="var(--primary)" />
      <rect x="7" y="8" width="5" height="16" rx="1.5" fill="#fff" />
      <rect x="14" y="8" width="5" height="10" rx="1.5" fill="#fff" opacity=".85" />
      <rect x="21" y="8" width="5" height="13" rx="1.5" fill="#fff" opacity=".7" />
    </svg>
  )
}

export function Sidebar({ collapsed, drawerOpen, onNavigate }: { collapsed: boolean; drawerOpen: boolean; onNavigate: () => void }) {
  const { wsId, workspace, canCreateProject } = useWorkspace()
  const { user, logout } = useAuth()
  const { data: workspaces } = useWorkspaces()
  const { data: projects } = useProjects(wsId)
  const { data: recent } = useRecentProjects(wsId)
  const { data: unread } = useUnreadCount(wsId)
  const { theme, toggle } = useTheme()
  const navigate = useNavigate()
  const [creating, setCreating] = useState(false)
  const favorites = projects?.filter((p) => p.favorite) ?? []
  const narrow = collapsed

  return (
    <aside
      aria-label="Sidebar"
      className={cn(
        'z-40 flex h-full shrink-0 flex-col border-r border-border bg-surface-2 transition-[width,transform] duration-200',
        'fixed inset-y-0 left-0 w-64 -translate-x-full lg:static lg:translate-x-0',
        drawerOpen && 'translate-x-0 shadow-xl',
        narrow ? 'lg:w-[60px]' : 'lg:w-60',
      )}
    >
      <Menu>
        <MenuTrigger className="m-2 flex items-center gap-2 rounded-lg p-1.5 text-left hover:bg-muted" aria-label="Switch workspace">
          <Logo />
          <span className={cn('min-w-0 flex-1', narrow && 'lg:hidden')}>
            <span className="block truncate text-[13px] font-semibold">{workspace.name}</span>
            <span className="block text-[11px] text-muted-foreground">Flowboard</span>
          </span>
          <ChevronsUpDown size={14} className={cn('text-muted-foreground', narrow && 'lg:hidden')} />
        </MenuTrigger>
        <MenuContent align="start" className="w-60">
          <MenuLabel>Workspaces</MenuLabel>
          {workspaces?.map((w) => (
            <MenuItem key={w.id} onSelect={() => navigate(`/w/${w.id}`)}>
              <span className="flex-1 truncate">{w.name}</span>
              {w.id === wsId && <Check size={14} />}
            </MenuItem>
          ))}
          <MenuSeparator />
          <MenuItem onSelect={() => navigate('/?new=1')}>
            <Plus size={14} /> New workspace
          </MenuItem>
        </MenuContent>
      </Menu>

      <nav className="space-y-0.5 px-2" aria-label="Main">
        {nav.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={label}
            to={`/w/${wsId}/${to}`}
            end={end}
            onClick={onNavigate}
            title={label}
            className={({ isActive }) =>
              cn(
                'flex items-center gap-2.5 rounded-md px-2.5 py-1.5 text-[13px] font-medium text-muted-foreground hover:bg-muted hover:text-foreground',
                isActive && 'bg-primary-soft text-primary hover:bg-primary-soft hover:text-primary',
                narrow && 'lg:justify-center lg:px-0',
              )
            }
          >
            <Icon size={16} />
            <span className={cn('flex-1', narrow && 'lg:hidden')}>{label}</span>
            {label === 'Inbox' && !!unread?.count && (
              <span className={cn('rounded-full bg-primary px-1.5 text-[10px] font-semibold text-primary-foreground', narrow && 'lg:hidden')}>
                {unread.count}
              </span>
            )}
          </NavLink>
        ))}
      </nav>

      <div className={cn('scroll-thin mt-4 min-h-0 flex-1 space-y-4 overflow-y-auto px-2', narrow && 'lg:hidden')}>
        {favorites.length > 0 && (
          <ProjectGroup title="Favorites" icon={<Star size={11} className="fill-current" />} projects={favorites} wsId={wsId} onNavigate={onNavigate} />
        )}
        {!!recent?.length && <ProjectGroup title="Recent" projects={recent} wsId={wsId} onNavigate={onNavigate} />}
        {canCreateProject && (
          <button
            onClick={() => setCreating(true)}
            className="flex w-full items-center gap-2 rounded-md px-2.5 py-1.5 text-[13px] text-muted-foreground hover:bg-muted hover:text-foreground"
          >
            <Plus size={14} /> New project
          </button>
        )}
      </div>
      {narrow && canCreateProject && (
        <button onClick={() => setCreating(true)} className="mx-auto mt-2 hidden rounded-md p-2 text-muted-foreground hover:bg-muted lg:block" aria-label="New project">
          <Plus size={16} />
        </button>
      )}

      <div className="mt-auto border-t border-border p-2">
        <Menu>
          <MenuTrigger className={cn('flex w-full items-center gap-2 rounded-lg p-1.5 text-left hover:bg-muted', narrow && 'lg:justify-center')} aria-label="Account menu">
            <Avatar user={user} size={28} />
            <span className={cn('min-w-0 flex-1', narrow && 'lg:hidden')}>
              <span className="block truncate text-[13px] font-medium">{user?.displayName}</span>
              <span className="block truncate text-[11px] text-muted-foreground">{user?.email}</span>
            </span>
          </MenuTrigger>
          <MenuContent align="start" side="top" className="w-56">
            <MenuItem onSelect={() => navigate(`/w/${wsId}/settings`)}>
              <Settings size={14} /> Profile & settings
            </MenuItem>
            <MenuItem onSelect={toggle}>
              {theme === 'dark' ? <Sun size={14} /> : <Moon size={14} />} {theme === 'dark' ? 'Light mode' : 'Dark mode'}
            </MenuItem>
            <MenuSeparator />
            <MenuItem
              onSelect={() => {
                logout()
                navigate('/login')
              }}
            >
              <LogOut size={14} /> Log out
            </MenuItem>
          </MenuContent>
        </Menu>
      </div>
      <ProjectDialog open={creating} onOpenChange={setCreating} />
    </aside>
  )
}

function ProjectGroup({
  title, icon, projects, wsId, onNavigate,
}: { title: string; icon?: React.ReactNode; projects: { id: string; key: string; name: string }[]; wsId: string; onNavigate: () => void }) {
  return (
    <div>
      <div className="mb-1 flex items-center gap-1 px-2.5 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
        {icon}
        {title}
      </div>
      {projects.map((p) => (
        <NavLink
          key={p.id}
          to={`/w/${wsId}/p/${p.id}/board`}
          onClick={onNavigate}
          className={({ isActive }) =>
            cn('flex items-center gap-2 rounded-md px-2.5 py-1.5 text-[13px] text-muted-foreground hover:bg-muted hover:text-foreground', isActive && 'text-foreground')
          }
        >
          <span className="grid h-4 w-4 place-items-center rounded bg-primary-soft text-[8px] font-bold text-primary">{p.key[0]}</span>
          <span className="truncate">{p.name}</span>
        </NavLink>
      ))}
    </div>
  )
}
