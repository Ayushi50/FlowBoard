import { Archive, Kanban, LayoutDashboard, List, MoreHorizontal, Pencil, Star, Trash2, GanttChart, ArchiveRestore } from 'lucide-react'
import { useEffect, useState } from 'react'
import { NavLink, Outlet, useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { ProjectDialog } from '@/components/ProjectDialog'
import { Skeleton } from '@/components/ui/bits'
import { Button } from '@/components/ui/button'
import { useConfirm } from '@/components/ui/confirm'
import { Menu, MenuContent, MenuItem, MenuSeparator, MenuTrigger } from '@/components/ui/dropdown'
import { EmptyState } from '@/components/ui/bits'
import { useProject, useProjectMutations } from '@/hooks/queries'
import { api, ApiError } from '@/lib/api'
import { cn } from '@/lib/utils'
import { useWorkspace } from '@/lib/workspace'

const tabs = [
  { to: '', label: 'Overview', icon: LayoutDashboard, end: true },
  { to: 'board', label: 'Board', icon: Kanban },
  { to: 'list', label: 'List', icon: List },
  { to: 'timeline', label: 'Timeline', icon: GanttChart },
]

export function ProjectLayout() {
  const { projectId = '' } = useParams()
  const { wsId } = useWorkspace()
  const navigate = useNavigate()
  const confirm = useConfirm()
  const { data: project, error, isLoading } = useProject(projectId)
  const m = useProjectMutations(wsId)
  const [editing, setEditing] = useState(false)

  useEffect(() => {
    if (project) api.post(`/projects/${projectId}/visit`).then(() => undefined, () => undefined)
  }, [projectId, project?.id]) // eslint-disable-line react-hooks/exhaustive-deps

  if (isLoading) return <div className="space-y-3 p-6"><Skeleton className="h-8 w-64" /><Skeleton className="h-64" /></div>
  if (error || !project) {
    return (
      <div className="p-6">
        <EmptyState title="Project not found" description="It may have been deleted, or you may not have access to it." action={<Button onClick={() => navigate(`/w/${wsId}/projects`)}>Back to projects</Button>} />
      </div>
    )
  }

  const remove = async () => {
    const ok = await confirm({
      title: `Delete ${project.name}?`,
      description: `This permanently deletes the project and all ${project.taskCount} of its tasks, comments and history. This cannot be undone. Consider archiving instead.`,
    })
    if (!ok) return
    try {
      await m.remove.mutateAsync(project.id)
      toast.success('Project deleted')
      navigate(`/w/${wsId}/projects`)
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Could not delete project')
    }
  }

  const archive = async () => {
    if (!project.archived) {
      const ok = await confirm({ title: `Archive ${project.name}?`, description: 'Archived projects become read-only and are hidden from lists. You can restore them later.', confirmLabel: 'Archive' })
      if (!ok) return
    }
    try {
      await m.archive.mutateAsync({ id: project.id, archived: !project.archived })
      toast.success(project.archived ? 'Project restored' : 'Project archived')
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Could not update project')
    }
  }

  return (
    <div className="flex h-full flex-col">
      <div className="shrink-0 border-b border-border bg-surface px-4 pt-3 sm:px-6">
        <div className="flex items-center gap-2">
          <span className="grid h-6 w-6 place-items-center rounded-md bg-primary-soft text-[11px] font-bold text-primary">{project.key[0]}</span>
          <h1 className="truncate text-[17px] font-semibold">{project.name}</h1>
          <span className="rounded bg-muted px-1.5 py-0.5 text-[11px] font-medium text-muted-foreground">{project.key}</span>
          {project.archived && <span className="rounded bg-warning/15 px-1.5 py-0.5 text-[11px] font-medium text-warning">Archived</span>}
          <Button
            variant="ghost" size="icon" className="ml-1" aria-pressed={project.favorite}
            aria-label={project.favorite ? 'Remove from favorites' : 'Add to favorites'}
            onClick={() => m.favorite.mutate({ id: project.id, on: !project.favorite })}
          >
            <Star size={15} className={cn(project.favorite && 'fill-amber-400 text-amber-400')} />
          </Button>
          {project.canManage && (
            <Menu>
              <MenuTrigger asChild><Button variant="ghost" size="icon" className="ml-auto" aria-label="Project actions"><MoreHorizontal size={16} /></Button></MenuTrigger>
              <MenuContent>
                <MenuItem onSelect={() => setEditing(true)}><Pencil size={14} /> Edit project</MenuItem>
                <MenuItem onSelect={archive}>{project.archived ? <ArchiveRestore size={14} /> : <Archive size={14} />}{project.archived ? 'Restore project' : 'Archive project'}</MenuItem>
                <MenuSeparator />
                <MenuItem danger onSelect={remove}><Trash2 size={14} /> Delete project</MenuItem>
              </MenuContent>
            </Menu>
          )}
        </div>
        <nav className="-mb-px mt-2 flex gap-1 overflow-x-auto" aria-label="Project views">
          {tabs.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={label}
              to={to || '.'}
              end={end}
              className={({ isActive }) =>
                cn('flex items-center gap-1.5 border-b-2 px-2.5 py-2 text-[13px] font-medium', isActive ? 'border-primary text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground')
              }
            >
              <Icon size={14} /> {label}
            </NavLink>
          ))}
        </nav>
      </div>
      <div className="min-h-0 flex-1 overflow-auto"><Outlet context={project} /></div>
      <ProjectDialog open={editing} onOpenChange={setEditing} project={project} />
    </div>
  )
}
