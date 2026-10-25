import { ListChecks } from 'lucide-react'
import { FilterBar } from '@/components/FilterBar'
import { TaskTable } from '@/components/TaskTable'
import { EmptyState, Skeleton } from '@/components/ui/bits'
import { useProjects, useWorkspaceTasks } from '@/hooks/queries'
import { useTaskFilters } from '@/hooks/useTaskFilters'
import { useWorkspace } from '@/lib/workspace'
import { Select } from '@/components/ui/input'

export function MyTasksPage() {
  const { wsId } = useWorkspace()
  const filters = useTaskFilters()
  const { state, params, setParams } = filters
  const { data: projects } = useProjects(wsId)
  const sort = state.sort || 'dueDate'
  const page = Number(params.get('page') ?? 0)
  const projectId = params.get('project') ?? ''
  // "My tasks" is every task assigned to me unless the user picks someone else explicitly.
  const { data, isLoading } = useWorkspaceTasks(wsId, {
    ...filters.api, mine: !filters.api.assigneeId, projectId: projectId || undefined, sort, dir: state.dir, page, size: 25,
  })

  return (
    <div className="mx-auto max-w-6xl space-y-3 px-4 py-5 sm:px-6">
      <div>
        <h1 className="text-lg font-semibold">My Tasks</h1>
        <p className="text-[13px] text-muted-foreground">Everything assigned to you across projects.</p>
      </div>
      <FilterBar
        filters={filters}
        showStatus
        extra={
          <Select value={projectId} onChange={(e) => setParams((q) => { const n = new URLSearchParams(q); if (e.target.value) n.set('project', e.target.value); else n.delete('project'); n.delete('page'); return n }, { replace: true })} className="w-40" aria-label="Filter by project">
            <option value="">All projects</option>
            {projects?.map((p) => <option key={p.id} value={p.id}>{p.key} · {p.name}</option>)}
          </Select>
        }
      />
      {isLoading || !data ? (
        <Skeleton className="h-64" />
      ) : data.totalItems === 0 && !filters.activeCount && !projectId ? (
        <EmptyState icon={<ListChecks size={20} />} title="Nothing assigned to you" description="When someone assigns you a task it will show up here." />
      ) : (
        <TaskTable
          page={data} sort={sort} dir={state.dir}
          onSort={(key) => filters.set({ sort: key, dir: sort === key && state.dir === 'asc' ? 'desc' : 'asc' })}
          onPage={(p) => setParams((q) => { const n = new URLSearchParams(q); n.set('page', String(p)); return n }, { replace: true })}
        />
      )}
    </div>
  )
}
