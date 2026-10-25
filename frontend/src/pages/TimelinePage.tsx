import { addDays, differenceInCalendarDays, eachWeekOfInterval, endOfDay, format, isWithinInterval, max as maxDate, min as minDate, parseISO, startOfDay } from 'date-fns'
import { Flag, Plus, Trash2 } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { toast } from 'sonner'
import { FilterBar } from '@/components/FilterBar'
import { EmptyState, Skeleton } from '@/components/ui/bits'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useMilestones, useProjectMutations, useProjectTasks } from '@/hooks/queries'
import { useCurrentProject } from '@/hooks/useProjectOutlet'
import { useTaskFilters } from '@/hooks/useTaskFilters'
import { useTaskPanel } from '@/hooks/useTaskPanel'
import { ApiError } from '@/lib/api'
import { cn } from '@/lib/utils'
import { useWorkspace } from '@/lib/workspace'

const DAY_PX = 28
const ROW = 34
const LABEL_W = 220
const BAR_COLOR = { BACKLOG: 'bg-zinc-400', TODO: 'bg-sky-500', IN_PROGRESS: 'bg-amber-500', DONE: 'bg-emerald-500' } as const

/** Gantt-style view: bars for tasks with dates, diamonds for milestones, a marker for today. Reads the same task query as Board/List. */
export function TimelinePage() {
  const project = useCurrentProject()
  const { wsId } = useWorkspace()
  const filters = useTaskFilters()
  const { open } = useTaskPanel()
  const { data, isLoading } = useProjectTasks(project.id, { ...filters.api, sort: 'dueDate', size: 500 })
  const { data: milestones } = useMilestones(project.id)
  const m = useProjectMutations(wsId)
  const [name, setName] = useState('')
  const [date, setDate] = useState('')
  const scroller = useRef<HTMLDivElement>(null)
  const scrolled = useRef(false)

  const rows = useMemo(
    () =>
      (data?.items ?? [])
        .filter((t) => t.startDate || t.dueDate)
        .map((t) => {
          const s = parseISO(t.startDate ?? t.dueDate!)
          const e = parseISO(t.dueDate ?? t.startDate!)
          return { task: t, start: minDate([s, e]), end: maxDate([s, e]) }
        })
        .sort((a, b) => a.start.getTime() - b.start.getTime()),
    [data],
  )
  const unscheduled = (data?.items ?? []).filter((t) => !t.startDate && !t.dueDate)

  const today = startOfDay(new Date())
  const points = [today, ...rows.flatMap((r) => [r.start, r.end]), ...(milestones ?? []).map((x) => parseISO(x.dueDate))]
  if (project.startDate) points.push(parseISO(project.startDate))
  if (project.targetDate) points.push(parseISO(project.targetDate))
  const rangeStart = addDays(startOfDay(minDate(points)), -3)
  const rangeEnd = addDays(startOfDay(maxDate(points)), 7)
  const totalDays = differenceInCalendarDays(rangeEnd, rangeStart) + 1
  const x = (d: Date) => differenceInCalendarDays(d, rangeStart) * DAY_PX
  const weeks = eachWeekOfInterval({ start: rangeStart, end: rangeEnd }, { weekStartsOn: 1 })
  const width = totalDays * DAY_PX

  const addMilestone = async (e: React.FormEvent) => {
    e.preventDefault()
    try {
      await m.addMilestone.mutateAsync({ id: project.id, name: name.trim(), dueDate: date })
      setName('')
      setDate('')
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Could not add milestone')
    }
  }

  // Open centred near today instead of at the far-left edge of the date range.
  useEffect(() => {
    if (scrolled.current || !scroller.current || rows.length === 0) return
    scrolled.current = true
    scroller.current.scrollLeft = Math.max(0, x(today) - 260)
  })

  if (isLoading) return <div className="p-6"><Skeleton className="h-64" /></div>

  return (
    <div className="space-y-3 px-4 py-3 sm:px-6">
      <FilterBar filters={filters} assignees={project.members} showStatus />
      {rows.length === 0 ? (
        <EmptyState title="Nothing scheduled yet" description="Give tasks a start or due date and they appear here as bars on the timeline." />
      ) : (
        <div className="overflow-hidden rounded-xl border border-border bg-surface">
          <div ref={scroller} className="scroll-thin overflow-auto" style={{ maxHeight: '60vh' }}>
            <div style={{ width: width + LABEL_W }} className="relative">
              {/* header */}
              <div className="sticky top-0 z-20 flex border-b border-border bg-surface" style={{ height: 30 }}>
                <div className="sticky left-0 z-30 shrink-0 border-r border-border bg-surface px-3 py-1.5 text-xs font-medium text-muted-foreground" style={{ width: LABEL_W }}>Task</div>
                <div className="relative" style={{ width }}>
                  {weeks.map((w) => (
                    <div key={w.toISOString()} className="absolute top-0 border-l border-border px-1.5 py-1.5 text-[11px] text-muted-foreground" style={{ left: x(w) }}>
                      {format(w, 'MMM d')}
                    </div>
                  ))}
                </div>
              </div>
              {/* milestone row */}
              {!!milestones?.length && (
                <div className="flex border-b border-border" style={{ height: ROW }}>
                  <div className="sticky left-0 z-10 flex shrink-0 items-center gap-1.5 border-r border-border bg-surface px-3 text-xs font-medium" style={{ width: LABEL_W }}><Flag size={12} className="text-primary" /> Milestones</div>
                  <div className="relative" style={{ width }}>
                    {milestones.map((ms) => (
                      <div key={ms.id} className="absolute top-1.5 flex items-center gap-1 text-[11px]" style={{ left: x(parseISO(ms.dueDate)) - 6 }} title={`${ms.name} · ${format(parseISO(ms.dueDate), 'MMM d, yyyy')}`}>
                        <span className="h-3 w-3 rotate-45 bg-primary" />
                        <span className="whitespace-nowrap font-medium">{ms.name}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
              {rows.map(({ task, start, end }) => {
                const left = x(start)
                const w = Math.max(DAY_PX, (differenceInCalendarDays(end, start) + 1) * DAY_PX)
                const late = task.overdue
                return (
                  <div key={task.id} className="flex border-b border-border/60 last:border-0" style={{ height: ROW }}>
                    <button onClick={() => open(task.id)} className="sticky left-0 z-10 flex shrink-0 items-center gap-2 border-r border-border bg-surface px-3 text-left text-[13px] hover:bg-muted" style={{ width: LABEL_W }}>
                      <span className="shrink-0 whitespace-nowrap text-xs text-muted-foreground">{task.identifier}</span>
                      <span className="truncate">{task.title}</span>
                    </button>
                    <div className="relative" style={{ width }}>
                      <button
                        onClick={() => open(task.id)}
                        title={`${task.title} · ${format(start, 'MMM d')} → ${format(end, 'MMM d')}`}
                        className={cn('absolute top-[7px] h-[20px] truncate rounded-md px-1.5 text-left text-[11px] font-medium text-white shadow-sm', BAR_COLOR[task.status], late && 'ring-2 ring-danger')}
                        style={{ left, width: w }}
                      >
                        {w > 70 ? task.title : ''}
                      </button>
                    </div>
                  </div>
                )
              })}
              {/* today & project range overlays */}
              <div className="pointer-events-none absolute bottom-0 top-0" style={{ left: LABEL_W, width }}>
                {isWithinInterval(today, { start: rangeStart, end: endOfDay(rangeEnd) }) && (
                  <div className="absolute bottom-0 top-0 w-px bg-danger/70" style={{ left: x(today) + DAY_PX / 2 }}>
                    <span className="absolute -top-0 left-1 rounded bg-danger px-1 text-[10px] text-white">Today</span>
                  </div>
                )}
                {project.targetDate && (
                  <div className="absolute bottom-0 top-0 w-px border-l border-dashed border-primary/70" style={{ left: x(parseISO(project.targetDate)) + DAY_PX }} title="Target deadline" />
                )}
              </div>
            </div>
          </div>
        </div>
      )}
      <div className="flex flex-wrap items-end justify-between gap-3">
        <p className="text-xs text-muted-foreground">
          {unscheduled.length > 0 && `${unscheduled.length} task${unscheduled.length > 1 ? 's' : ''} without dates are not shown. `}
          Dashed line = target deadline{project.targetDate ? ` (${format(parseISO(project.targetDate), 'MMM d')})` : ''}.
        </p>
        {project.canManage && !project.archived && (
          <form onSubmit={addMilestone} className="flex items-center gap-2">
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="New milestone" className="w-40" aria-label="Milestone name" required />
            <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} className="w-36" aria-label="Milestone date" required />
            <Button type="submit" disabled={!name.trim() || !date}><Plus size={13} /> Milestone</Button>
          </form>
        )}
      </div>
      {project.canManage && !!milestones?.length && (
        <ul className="flex flex-wrap gap-2">
          {milestones.map((ms) => (
            <li key={ms.id} className="flex items-center gap-1.5 rounded-full border border-border px-2.5 py-0.5 text-xs">
              {ms.name} · {format(parseISO(ms.dueDate), 'MMM d')}
              <button onClick={() => m.deleteMilestone.mutate(ms.id)} aria-label={`Delete milestone ${ms.name}`} className="text-muted-foreground hover:text-danger"><Trash2 size={11} /></button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
