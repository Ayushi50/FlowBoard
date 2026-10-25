import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useTaskPanel } from '@/hooks/useTaskPanel'
import type { Page, Task } from '@/lib/types'
import { cn, STATUS_LABEL } from '@/lib/utils'
import { DueChip } from './TaskCard'
import { Avatar } from './ui/avatar'
import { LabelChip, PriorityIcon, SortIcon, StatusDot } from './ui/bits'
import { Button } from './ui/button'

const COLUMNS: { key: string; label: string; className?: string }[] = [
  { key: 'key', label: 'ID', className: 'w-24' },
  { key: 'title', label: 'Title' },
  { key: 'status', label: 'Status', className: 'w-32' },
  { key: 'priority', label: 'Priority', className: 'w-28' },
  { key: 'assignee', label: 'Assignee', className: 'w-40' },
  { key: 'dueDate', label: 'Due', className: 'w-28' },
]

export function TaskTable({
  page, sort, dir, onSort, onPage, emptyText = 'No tasks match these filters.',
}: { page: Page<Task>; sort: string; dir: 'asc' | 'desc'; onSort: (key: string) => void; onPage: (p: number) => void; emptyText?: string }) {
  const { open } = useTaskPanel()
  return (
    <div>
      <div className="overflow-x-auto rounded-xl border border-border bg-surface">
        <table className="w-full min-w-[760px] text-left text-[13px]">
          <thead>
            <tr className="border-b border-border text-xs text-muted-foreground">
              {COLUMNS.map((c) => (
                <th key={c.key} className={cn('px-3 py-2 font-medium', c.className)} aria-sort={sort === c.key ? (dir === 'asc' ? 'ascending' : 'descending') : 'none'}>
                  <button className="inline-flex items-center gap-1 hover:text-foreground" onClick={() => onSort(c.key)}>
                    {c.label}
                    <SortIcon dir={sort === c.key ? dir : undefined} />
                  </button>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {page.items.map((t) => (
              <tr key={t.id} onClick={() => open(t.id)} className="cursor-pointer border-b border-border last:border-0 hover:bg-muted/60" data-testid={`row-${t.identifier}`}>
                <td className="px-3 py-2 text-xs font-medium text-muted-foreground">{t.identifier}</td>
                <td className="px-3 py-2">
                  <div className="flex items-center gap-2">
                    <span className="truncate font-medium">{t.title}</span>
                    {t.labels.slice(0, 2).map((l) => <LabelChip key={l.id} label={l} />)}
                  </div>
                </td>
                <td className="px-3 py-2"><StatusDot status={t.status} withLabel /></td>
                <td className="px-3 py-2"><PriorityIcon priority={t.priority} withLabel /></td>
                <td className="px-3 py-2">
                  <span className="flex items-center gap-2">
                    <Avatar user={t.assignee} size={20} />
                    <span className="truncate">{t.assignee?.displayName ?? <span className="text-muted-foreground">Unassigned</span>}</span>
                  </span>
                </td>
                <td className="px-3 py-2"><DueChip task={t} />{!t.dueDate && <span className="text-muted-foreground">—</span>}</td>
              </tr>
            ))}
            {page.items.length === 0 && (
              <tr><td colSpan={COLUMNS.length} className="px-3 py-10 text-center text-muted-foreground">{emptyText}</td></tr>
            )}
          </tbody>
        </table>
      </div>
      <div className="mt-3 flex items-center justify-between text-xs text-muted-foreground">
        <span>
          {page.totalItems === 0 ? '0 tasks' : `${page.page * page.size + 1}–${page.page * page.size + page.items.length} of ${page.totalItems}`}
          <span className="sr-only"> ({STATUS_LABEL.TODO})</span>
        </span>
        <div className="flex items-center gap-1">
          <Button size="sm" disabled={page.page === 0} onClick={() => onPage(page.page - 1)} aria-label="Previous page"><ChevronLeft size={14} /></Button>
          <span>Page {page.page + 1} of {Math.max(page.totalPages, 1)}</span>
          <Button size="sm" disabled={page.page + 1 >= page.totalPages} onClick={() => onPage(page.page + 1)} aria-label="Next page"><ChevronRight size={14} /></Button>
        </div>
      </div>
    </div>
  )
}
