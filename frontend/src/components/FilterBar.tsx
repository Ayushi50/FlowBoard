import { Search, X } from 'lucide-react'
import { useLabels, useMembers } from '@/hooks/queries'
import type { useTaskFilters } from '@/hooks/useTaskFilters'
import type { DueFilter } from '@/hooks/useTaskFilters'
import type { User } from '@/lib/types'
import { PRIORITIES, STATUSES } from '@/lib/types'
import { cn, PRIORITY_LABEL, STATUS_LABEL } from '@/lib/utils'
import { useWorkspace } from '@/lib/workspace'
import { Button } from './ui/button'
import { Input, Select } from './ui/input'
import { Popover, PopoverContent, PopoverTrigger } from './ui/popover'

function MultiPick<T extends string>({
  label, options, value, onChange,
}: { label: string; options: { value: T; label: string; color?: string }[]; value: T[]; onChange: (v: T[]) => void }) {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button size="md" className={cn(value.length && 'border-primary text-primary')} aria-label={`Filter by ${label}`}>
          {label}
          {value.length > 0 && <span className="rounded-full bg-primary px-1.5 text-[10px] text-primary-foreground">{value.length}</span>}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-52 p-1">
        {options.map((o) => (
          <label key={o.value} className="flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-[13px] hover:bg-muted">
            <input
              type="checkbox"
              checked={value.includes(o.value)}
              onChange={() => onChange(value.includes(o.value) ? value.filter((v) => v !== o.value) : [...value, o.value])}
              className="accent-[var(--primary)]"
            />
            {o.color && <span className="h-2.5 w-2.5 rounded-full" style={{ background: o.color }} />}
            {o.label}
          </label>
        ))}
      </PopoverContent>
    </Popover>
  )
}

export function FilterBar({
  filters, assignees, showStatus, extra,
}: { filters: ReturnType<typeof useTaskFilters>; assignees?: User[]; showStatus?: boolean; extra?: React.ReactNode }) {
  const { wsId } = useWorkspace()
  const { data: labels } = useLabels(wsId)
  const { data: members } = useMembers(wsId)
  const { state, set, clear, activeCount } = filters
  const people = assignees ?? members?.map((m) => m.user) ?? []

  return (
    <div className="flex flex-wrap items-center gap-2" role="search" aria-label="Task filters">
      <div className="relative">
        <Search size={13} className="absolute left-2.5 top-2.5 text-muted-foreground" />
        <Input value={state.q} onChange={(e) => set({ q: e.target.value })} placeholder="Search tasks…" className="w-44 pl-7 sm:w-56" aria-label="Search tasks" />
      </div>
      <Select value={state.assignee} onChange={(e) => set({ assignee: e.target.value })} className="w-36" aria-label="Filter by assignee">
        <option value="">All assignees</option>
        <option value="me">Assigned to me</option>
        {people.map((u) => <option key={u.id} value={u.id}>{u.displayName}</option>)}
      </Select>
      <MultiPick label="Priority" value={state.priority} onChange={(v) => set({ priority: v })} options={PRIORITIES.map((p) => ({ value: p, label: PRIORITY_LABEL[p] }))} />
      {showStatus && <MultiPick label="Status" value={state.status} onChange={(v) => set({ status: v })} options={STATUSES.map((s) => ({ value: s, label: STATUS_LABEL[s] }))} />}
      <MultiPick label="Labels" value={state.label} onChange={(v) => set({ label: v })} options={(labels ?? []).map((l) => ({ value: l.id, label: l.name, color: l.color }))} />
      <Select value={state.due} onChange={(e) => set({ due: e.target.value as DueFilter })} className="w-36" aria-label="Filter by due date">
        <option value="">Any due date</option>
        <option value="overdue">Overdue</option>
        <option value="today">Due today</option>
        <option value="week">Due this week</option>
        <option value="next7">Next 7 days</option>
      </Select>
      {activeCount > 0 && (
        <Button variant="ghost" size="md" onClick={clear}><X size={13} /> Clear <span className="text-muted-foreground">({activeCount})</span></Button>
      )}
      {extra}
    </div>
  )
}
