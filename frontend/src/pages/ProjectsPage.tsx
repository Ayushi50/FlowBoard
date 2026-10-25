import { Archive, FolderKanban, Plus, Star } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ProjectDialog } from '@/components/ProjectDialog'
import { AvatarStack } from '@/components/ui/avatar'
import { EmptyState, ProgressBar, Skeleton } from '@/components/ui/bits'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useProjectMutations, useProjects } from '@/hooks/queries'
import { cn, formatDate } from '@/lib/utils'
import { useWorkspace } from '@/lib/workspace'

export function ProjectsPage() {
  const { wsId, canCreateProject } = useWorkspace()
  const [archived, setArchived] = useState(false)
  const [q, setQ] = useState('')
  const [creating, setCreating] = useState(false)
  const { data, isLoading } = useProjects(wsId, archived)
  const m = useProjectMutations(wsId)
  const list = (data ?? []).filter((p) => (archived ? p.archived : true) && (p.name + p.key).toLowerCase().includes(q.toLowerCase()))

  return (
    <div className="mx-auto max-w-6xl space-y-4 px-4 py-6 sm:px-6">
      <header className="flex flex-wrap items-center gap-3">
        <div className="flex-1"><h1 className="text-xl font-semibold">Projects</h1><p className="text-[13px] text-muted-foreground">Everything your team is working on.</p></div>
        <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Filter projects" className="w-48" aria-label="Filter projects" />
        <Button onClick={() => setArchived((a) => !a)} aria-pressed={archived}><Archive size={13} /> {archived ? 'Showing archived' : 'Show archived'}</Button>
        {canCreateProject && <Button variant="primary" onClick={() => setCreating(true)}><Plus size={14} /> New project</Button>}
      </header>
      {isLoading ? (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-36" />)}</div>
      ) : list.length === 0 ? (
        <EmptyState icon={<FolderKanban size={22} />} title={archived ? 'No archived projects' : 'No projects yet'} description={canCreateProject ? 'Create a project to start planning work.' : 'You have not been added to a project yet.'}
          action={canCreateProject && !archived ? <Button variant="primary" onClick={() => setCreating(true)}>New project</Button> : undefined} />
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {list.map((p) => (
            <div key={p.id} className={cn('relative rounded-xl border border-border bg-surface p-4 transition-colors hover:border-primary/50', p.archived && 'opacity-70')}>
              <Link to={`/w/${wsId}/p/${p.id}`} className="absolute inset-0 rounded-xl" aria-label={`Open ${p.name}`} />
              <div className="flex items-start gap-2">
                <span className="grid h-7 w-7 shrink-0 place-items-center rounded-md bg-primary-soft text-xs font-bold text-primary">{p.key[0]}</span>
                <div className="min-w-0 flex-1"><div className="truncate font-semibold">{p.name}</div><div className="text-xs text-muted-foreground">{p.key} · {p.status.replace('_', ' ').toLowerCase()}</div></div>
                <button className="relative z-10 rounded p-1 hover:bg-muted" aria-pressed={p.favorite} aria-label={p.favorite ? `Unfavorite ${p.name}` : `Favorite ${p.name}`}
                  onClick={() => m.favorite.mutate({ id: p.id, on: !p.favorite })}><Star size={15} className={cn(p.favorite && 'fill-amber-400 text-amber-400')} /></button>
              </div>
              <p className="mt-2 line-clamp-2 min-h-[36px] text-[13px] text-muted-foreground">{p.description || 'No description'}</p>
              <div className="mt-3"><div className="mb-1 flex justify-between text-xs text-muted-foreground"><span>{p.doneCount}/{p.taskCount} tasks</span><span>{p.completionPercent}%</span></div><ProgressBar percent={p.completionPercent} /></div>
              <div className="mt-3 flex items-center justify-between"><AvatarStack users={p.members} /><span className="text-xs text-muted-foreground">{p.targetDate ? `Due ${formatDate(p.targetDate)}` : ''}</span></div>
            </div>
          ))}
        </div>
      )}
      <ProjectDialog open={creating} onOpenChange={setCreating} />
    </div>
  )
}
