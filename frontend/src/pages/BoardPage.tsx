import { SlidersHorizontal } from 'lucide-react'
import { Board } from '@/components/Board'
import { FilterBar } from '@/components/FilterBar'
import { Skeleton } from '@/components/ui/bits'
import { Select } from '@/components/ui/input'
import { useProjectTasks } from '@/hooks/queries'
import { useCurrentProject } from '@/hooks/useProjectOutlet'
import { useTaskFilters } from '@/hooks/useTaskFilters'

export function BoardPage() {
  const project = useCurrentProject()
  const filters = useTaskFilters()
  const sort = filters.state.sort || 'position'
  const { data, isLoading, isError, refetch } = useProjectTasks(project.id, { ...filters.api, sort, dir: 'asc', size: 500 })

  return (
    <div className="flex h-full flex-col">
      <div className="shrink-0 px-4 py-3 sm:px-6">
        <FilterBar
          filters={filters}
          assignees={project.members}
          extra={
            <label className="ml-auto flex items-center gap-1.5 text-xs text-muted-foreground">
              <SlidersHorizontal size={13} />
              <Select value={sort} onChange={(e) => filters.set({ sort: e.target.value === 'position' ? null : e.target.value })} className="w-36" aria-label="Sort board">
                <option value="position">Manual order</option>
                <option value="priority">Priority</option>
                <option value="dueDate">Due date</option>
                <option value="updatedAt">Recently updated</option>
              </Select>
            </label>
          }
        />
      </div>
      <div className="min-h-0 flex-1">
        {isLoading && <div className="flex gap-3 px-6">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-72 w-[286px]" />)}</div>}
        {isError && <p className="px-6 text-[13px]">Could not load tasks. <button className="underline" onClick={() => refetch()}>Retry</button></p>}
        {data && <Board tasks={data.items} projectId={project.id} canEdit={!project.archived} reorderable={sort === 'position' && !filters.activeCount} />}
      </div>
      {data && data.totalItems > data.items.length && <p className="px-6 pb-2 text-xs text-muted-foreground">Showing the first {data.items.length} of {data.totalItems} tasks — use filters to narrow down.</p>}
    </div>
  )
}
