import {
  closestCorners, DndContext, DragOverlay, KeyboardSensor, PointerSensor, useDroppable, useSensor, useSensors,
  type DragEndEvent, type DragOverEvent, type DragStartEvent,
} from '@dnd-kit/core'
import { SortableContext, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { Plus } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import { useCreateTask, useMoveTask } from '@/hooks/queries'
import { useTaskPanel } from '@/hooks/useTaskPanel'
import { ApiError } from '@/lib/api'
import type { Task, TaskStatus } from '@/lib/types'
import { STATUSES } from '@/lib/types'
import { cn, STATUS_LABEL } from '@/lib/utils'
import { TaskCardView } from './TaskCard'
import { StatusDot } from './ui/bits'
import { Button } from './ui/button'
import { Input } from './ui/input'

type Columns = Record<TaskStatus, Task[]>

export const groupByStatus = (tasks: Task[]): Columns => {
  const cols: Columns = { BACKLOG: [], TODO: [], IN_PROGRESS: [], DONE: [] }
  tasks.forEach((t) => cols[t.status].push(t))
  return cols
}

function SortableCard({ task, disabled }: { task: Task; disabled: boolean }) {
  const { open } = useTaskPanel()
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: task.id, data: { type: 'task' }, disabled })
  return (
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={cn('touch-manipulation', isDragging && 'opacity-30')}
      {...attributes}
      {...listeners}
      data-testid={`card-${task.identifier}`}
    >
      <TaskCardView task={task} onOpen={() => open(task.id)} />
    </div>
  )
}

function Column({ status, tasks, projectId, canAdd, reorderable }: { status: TaskStatus; tasks: Task[]; projectId: string; canAdd: boolean; reorderable: boolean }) {
  const { setNodeRef, isOver } = useDroppable({ id: status, data: { type: 'column' } })
  const [adding, setAdding] = useState(false)
  const [title, setTitle] = useState('')
  const create = useCreateTask(projectId)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!title.trim()) return
    try {
      await create.mutateAsync({ title: title.trim(), status })
      setTitle('')
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Could not create task')
    }
  }

  return (
    <section
      aria-label={`${STATUS_LABEL[status]} column`}
      className={cn('flex w-[286px] shrink-0 flex-col rounded-xl bg-surface-2 transition-colors', isOver && 'bg-primary-soft/60')}
    >
      <header className="flex items-center gap-2 px-3 py-2.5">
        <StatusDot status={status} />
        <h3 className="text-[13px] font-semibold">{STATUS_LABEL[status]}</h3>
        <span className="rounded-full bg-muted px-1.5 text-[11px] text-muted-foreground" aria-label={`${tasks.length} tasks`}>{tasks.length}</span>
        {canAdd && (
          <Button variant="ghost" size="icon" className="ml-auto h-6 w-6" onClick={() => setAdding(true)} aria-label={`Add task to ${STATUS_LABEL[status]}`}>
            <Plus size={14} />
          </Button>
        )}
      </header>
      <div ref={setNodeRef} className="scroll-thin flex min-h-[60px] flex-1 flex-col gap-2 overflow-y-auto px-2 pb-2">
        <SortableContext items={tasks.map((t) => t.id)} strategy={verticalListSortingStrategy}>
          {tasks.map((t) => <SortableCard key={t.id} task={t} disabled={!canAdd} />)}
        </SortableContext>
        {adding && (
          <form onSubmit={submit} className="space-y-1.5">
            <Input
              autoFocus
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              onKeyDown={(e) => e.key === 'Escape' && (setAdding(false), setTitle(''))}
              placeholder="Task title, then Enter"
              aria-label={`New task in ${STATUS_LABEL[status]}`}
              maxLength={255}
            />
            <div className="flex gap-1.5">
              <Button type="submit" variant="primary" size="sm" disabled={!title.trim() || create.isPending}>Add</Button>
              <Button type="button" size="sm" variant="ghost" onClick={() => { setAdding(false); setTitle('') }}>Cancel</Button>
            </div>
          </form>
        )}
        {tasks.length === 0 && !adding && (
          <p className="px-2 py-4 text-center text-xs text-muted-foreground">{reorderable ? 'Drop tasks here' : 'No tasks'}</p>
        )}
      </div>
    </section>
  )
}

export function Board({ tasks, projectId, canEdit, reorderable }: { tasks: Task[]; projectId: string; canEdit: boolean; reorderable: boolean }) {
  const [cols, setCols] = useState<Columns>(() => groupByStatus(tasks))
  const [active, setActive] = useState<Task | null>(null)
  const dragging = useRef(false)
  const move = useMoveTask()

  useEffect(() => {
    if (!dragging.current) setCols(groupByStatus(tasks))
  }, [tasks])

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  const containerOf = (id: string): TaskStatus | undefined =>
    (STATUSES as string[]).includes(id) ? (id as TaskStatus) : STATUSES.find((s) => cols[s].some((t) => t.id === id))

  const onDragStart = (e: DragStartEvent) => {
    dragging.current = true
    setActive(STATUSES.flatMap((s) => cols[s]).find((t) => t.id === e.active.id) ?? null)
  }

  const onDragOver = (e: DragOverEvent) => {
    const { active: a, over } = e
    if (!over) return
    const from = containerOf(String(a.id))
    const to = containerOf(String(over.id))
    if (!from || !to || from === to) return
    setCols((prev) => {
      const item = prev[from].find((t) => t.id === a.id)
      if (!item) return prev
      const overIndex = prev[to].findIndex((t) => t.id === over.id)
      const index = overIndex >= 0 ? overIndex : prev[to].length
      return {
        ...prev,
        [from]: prev[from].filter((t) => t.id !== a.id),
        [to]: [...prev[to].slice(0, index), { ...item, status: to }, ...prev[to].slice(index)],
      }
    })
  }

  const onDragEnd = async (e: DragEndEvent) => {
    const { active: a, over } = e
    setActive(null)
    const original = tasks.find((t) => t.id === a.id)
    const to = over ? containerOf(String(over.id)) : undefined
    if (!over || !original || !to) {
      dragging.current = false
      setCols(groupByStatus(tasks))
      return
    }
    // Final order within the destination column (reorder in place when dropped on a sibling).
    let column = cols[to]
    const oldIndex = column.findIndex((t) => t.id === a.id)
    const overIndex = column.findIndex((t) => t.id === over.id)
    if (oldIndex >= 0 && overIndex >= 0 && oldIndex !== overIndex) {
      column = [...column]
      const [item] = column.splice(oldIndex, 1)
      column.splice(overIndex, 0, item)
      setCols((prev) => ({ ...prev, [to]: column }))
    }
    const index = column.findIndex((t) => t.id === a.id)
    const originalColumn = tasks.filter((t) => t.status === original.status)
    const unchanged = original.status === to && (!reorderable || originalColumn.findIndex((t) => t.id === a.id) === index)
    dragging.current = false
    if (unchanged) {
      setCols(groupByStatus(tasks))
      return
    }
    const before = reorderable ? column[index + 1]?.id : undefined
    const after = reorderable ? column[index - 1]?.id : undefined
    try {
      await move.mutateAsync({
        task: original,
        status: to,
        afterTaskId: after ?? null,
        beforeTaskId: after ? null : before ?? null,
      })
    } catch (err) {
      if (err instanceof ApiError && err.isConflict) {
        toast.warning('Someone else changed this task, so your move was not applied. The board now shows the latest version.')
      } else {
        toast.error(err instanceof ApiError ? err.message : 'Could not move task')
      }
    }
  }

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCorners}
      onDragStart={onDragStart}
      onDragOver={onDragOver}
      onDragEnd={onDragEnd}
      onDragCancel={() => { dragging.current = false; setActive(null); setCols(groupByStatus(tasks)) }}
    >
      <div className="scroll-thin flex h-full gap-3 overflow-x-auto px-4 pb-4 pt-1 sm:px-6">
        {STATUSES.map((s) => <Column key={s} status={s} tasks={cols[s]} projectId={projectId} canAdd={canEdit} reorderable={reorderable} />)}
      </div>
      <DragOverlay dropAnimation={{ duration: 180 }}>{active ? <div className="w-[270px]"><TaskCardView task={active} dragging /></div> : null}</DragOverlay>
    </DndContext>
  )
}
