import { CheckSquare, MessageSquare, Paperclip } from 'lucide-react'
import type { Task } from '@/lib/types'
import { cn, formatDue } from '@/lib/utils'
import { Avatar } from './ui/avatar'
import { LabelChip, PriorityIcon } from './ui/bits'

export function DueChip({ task }: { task: Pick<Task, 'dueDate' | 'overdue' | 'status'> }) {
  if (!task.dueDate) return null
  return (
    <span
      className={cn('rounded px-1 text-[11px]', task.overdue ? 'bg-danger/10 font-medium text-danger' : 'text-muted-foreground')}
      title={task.overdue ? 'Overdue' : 'Due date'}
    >
      {task.overdue && <span className="sr-only">Overdue: </span>}
      {formatDue(task.dueDate)}
    </span>
  )
}

export function TaskCardView({ task, dragging, onOpen }: { task: Task; dragging?: boolean; onOpen?: () => void }) {
  const checklist = task.checklistTotal + task.subtaskTotal
  const checklistDone = task.checklistDone + task.subtaskDone
  return (
    <div
      onClick={onOpen}
      className={cn(
        'group rounded-lg border border-border bg-surface p-2.5 text-left shadow-sm transition-[box-shadow,border-color] hover:border-muted-foreground/40',
        task.overdue && 'border-l-2 border-l-danger',
        dragging && 'rotate-1 cursor-grabbing shadow-lg ring-1 ring-primary/40',
      )}
    >
      <div className="mb-1 flex items-center justify-between text-[11px] text-muted-foreground">
        <span className="font-medium">{task.identifier}</span>
        <PriorityIcon priority={task.priority} />
      </div>
      <p className="text-[13px] font-medium leading-snug">{task.title}</p>
      {task.labels.length > 0 && (
        <div className="mt-1.5 flex flex-wrap gap-1">{task.labels.map((l) => <LabelChip key={l.id} label={l} />)}</div>
      )}
      <div className="mt-2 flex items-center gap-2.5 text-[11px] text-muted-foreground">
        <DueChip task={task} />
        {task.commentCount > 0 && <span className="inline-flex items-center gap-0.5" title="Comments"><MessageSquare size={11} />{task.commentCount}</span>}
        {task.attachmentCount > 0 && <span className="inline-flex items-center gap-0.5" title="Attachments"><Paperclip size={11} />{task.attachmentCount}</span>}
        {checklist > 0 && <span className="inline-flex items-center gap-0.5" title="Subtasks and checklist"><CheckSquare size={11} />{checklistDone}/{checklist}</span>}
        <span className="ml-auto"><Avatar user={task.assignee} size={20} /></span>
      </div>
    </div>
  )
}
