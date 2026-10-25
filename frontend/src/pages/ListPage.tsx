import { FilterBar } from '@/components/FilterBar'
import { TaskTable } from '@/components/TaskTable'
import { Skeleton } from '@/components/ui/bits'
import { useProjectTasks } from '@/hooks/queries'
import { useCurrentProject } from '@/hooks/useProjectOutlet'
import { useTaskFilters } from '@/hooks/useTaskFilters'

export function ListPage() {
  const project = useCurrentProject()
  const filters = useTaskFilters()
  const { state, params, setParams } = filters
  const sort = state.sort || 'key'
  const page = Number(params.get('page') ?? 0)
  const { data, isLoading } = useProjectTasks(project.id, { ...filters.api, sort, dir: state.dir, page, size: 25 })

  const onSort = (key: string) => filters.set({ sort: key, dir: sort === key && state.dir === 'asc' ? 'desc' : 'asc' })
  const onPage = (p: number) => setParams((q) => { const n = new URLSearchParams(q); n.set('page', String(p)); return n }, { replace: true })

  return (
    <div className="space-y-3 px-4 py-3 sm:px-6">
      <FilterBar filters={filters} assignees={project.members} showStatus />
      {isLoading || !data ? <Skeleton className="h-64" /> : <TaskTable page={data} sort={sort} dir={state.dir} onSort={onSort} onPage={onPage} />}
    </div>
  )
}
