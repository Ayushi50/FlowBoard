import { Check, MessageSquare, Paperclip, Plus, Trash2, X, MoreHorizontal, Link2 } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { toast } from 'sonner'
import {
  useLabels, useMoveTask, useProject, useTaskActivity, useTaskComments, useTaskDetail, useTaskSubResources,
  useUpdateTask, useDeleteTask, type TaskInput,
} from '@/hooks/queries'
import { useTaskPanel } from '@/hooks/useTaskPanel'
import { ApiError } from '@/lib/api'
import type { Task, TaskStatus, User } from '@/lib/types'
import { PRIORITIES, STATUSES, TYPES } from '@/lib/types'
import { cn, formatBytes, formatDate, PRIORITY_LABEL, STATUS_LABEL, timeAgo, TYPE_LABEL } from '@/lib/utils'
import { useWorkspace } from '@/lib/workspace'
import { Avatar } from './ui/avatar'
import { LabelChip, ProgressBar, Skeleton } from './ui/bits'
import { Button } from './ui/button'
import { useConfirm } from './ui/confirm'
import { Dialog, DialogClose, DialogContent } from './ui/dialog'
import { Menu, MenuContent, MenuItem, MenuTrigger } from './ui/dropdown'
import { Input, Select, Textarea } from './ui/input'
import { Popover, PopoverContent, PopoverTrigger } from './ui/popover'

export function TaskPanel() {
  const { taskId, close } = useTaskPanel()
  return (
    <Dialog open={!!taskId} onOpenChange={(o) => !o && close()}>
      <DialogContent title="Task details" side="right">
        {taskId && <TaskPanelBody taskId={taskId} onClose={close} />}
      </DialogContent>
    </Dialog>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid grid-cols-[104px_1fr] items-center gap-2 py-1">
      <span className="text-xs text-muted-foreground">{label}</span>
      <div className="min-w-0">{children}</div>
    </div>
  )
}

function TaskPanelBody({ taskId, onClose }: { taskId: string; onClose: () => void }) {
  const { data, isLoading, error } = useTaskDetail(taskId)
  const { wsId } = useWorkspace()
  const confirm = useConfirm()
  const del = useDeleteTask()
  const { open } = useTaskPanel()

  if (isLoading) {
    return <div className="space-y-4 p-6"><Skeleton className="h-6 w-1/3" /><Skeleton className="h-8 w-3/4" /><Skeleton className="h-40" /></div>
  }
  if (error || !data) {
    return (
      <div className="p-6">
        <p className="text-[13px]">{error instanceof ApiError && error.status === 404 ? 'This task no longer exists or you do not have access.' : 'Could not load the task.'}</p>
        <Button className="mt-3" onClick={onClose}>Close</Button>
      </div>
    )
  }
  const { task } = data

  const copyLink = async () => {
    try {
      await navigator.clipboard.writeText(`${location.origin}/w/${wsId}/p/${task.projectId}/board?task=${task.id}`)
      toast.success('Link copied')
    } catch {
      toast.error('Could not copy link')
    }
  }

  const remove = async () => {
    const ok = await confirm({
      title: `Delete ${task.identifier}?`,
      description: 'This permanently removes the task, its comments, checklist and subtasks. This cannot be undone.',
    })
    if (!ok) return
    try {
      await del.mutateAsync(task.id)
      toast.success(`Deleted ${task.identifier}`)
      onClose()
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Could not delete task')
    }
  }

  return (
    <>
      <div className="flex items-center justify-between border-b border-border px-5 py-3">
        <div className="flex items-center gap-2 text-[13px] text-muted-foreground">
          {task.parentId && (
            <>
              <button onClick={() => open(task.parentId!)} className="hover:text-foreground hover:underline">{task.parentIdentifier}</button>
              <span>/</span>
            </>
          )}
          <span className="font-medium text-foreground">{task.identifier}</span>
        </div>
        <div className="flex items-center gap-1">
          <Button variant="ghost" size="icon" onClick={copyLink} aria-label="Copy link"><Link2 size={15} /></Button>
          <Menu>
            <MenuTrigger asChild><Button variant="ghost" size="icon" aria-label="More actions"><MoreHorizontal size={16} /></Button></MenuTrigger>
            <MenuContent><MenuItem danger onSelect={remove}><Trash2 size={14} /> Delete task</MenuItem></MenuContent>
          </Menu>
          <DialogClose asChild><Button variant="ghost" size="icon" aria-label="Close panel"><X size={16} /></Button></DialogClose>
        </div>
      </div>
      <div className="scroll-thin min-h-0 flex-1 overflow-y-auto">
        <TaskForm task={task} />
        <Checklist taskId={task.id} items={data.checklist} />
        <Subtasks task={task} subtasks={data.subtasks} />
        <Attachments taskId={task.id} items={data.attachments} />
        <Discussion task={task} />
        <div className="border-t border-border px-5 py-3 text-xs text-muted-foreground">
          Created {formatDate(task.createdAt)} by {task.reporter.displayName} · Updated {timeAgo(task.updatedAt)}
        </div>
      </div>
    </>
  )
}

function useSave(task: Task) {
  const update = useUpdateTask()
  return async (patch: Partial<TaskInput>) => {
    try {
      await update.mutateAsync({ task, patch })
    } catch (e) {
      if (e instanceof ApiError && e.isConflict) {
        toast.warning('Someone else edited this task. Your change was not applied — showing the latest version.')
      } else {
        toast.error(e instanceof ApiError ? e.message : 'Could not save changes')
      }
    }
  }
}

function TaskForm({ task }: { task: Task }) {
  const { wsId } = useWorkspace()
  const save = useSave(task)
  const move = useMoveTask()
  const { data: project } = useProject(task.projectId)
  const { data: labels } = useLabels(wsId)
  const [title, setTitle] = useState(task.title)
  const [description, setDescription] = useState(task.description ?? '')

  useEffect(() => {
    setTitle(task.title)
    setDescription(task.description ?? '')
  }, [task.id, task.version, task.title, task.description])

  const changeStatus = async (status: TaskStatus) => {
    try {
      await move.mutateAsync({ task, status })
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Could not change status')
    }
  }

  const toggleLabel = (id: string) => {
    const ids = task.labels.map((l) => l.id)
    save({ labelIds: ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id] })
  }

  return (
    <div className="px-5 pb-2 pt-4">
      <input
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        onBlur={() => title.trim() && title.trim() !== task.title && save({ title: title.trim() })}
        onKeyDown={(e) => e.key === 'Enter' && (e.target as HTMLInputElement).blur()}
        aria-label="Task title"
        maxLength={255}
        className="w-full rounded-md bg-transparent px-1 py-1 text-xl font-semibold hover:bg-muted focus:bg-muted"
      />
      <div className="mt-3">
        <Row label="Status">
          <Select value={task.status} onChange={(e) => changeStatus(e.target.value as TaskStatus)} aria-label="Status">
            {STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
          </Select>
        </Row>
        <Row label="Priority">
          <Select value={task.priority} onChange={(e) => save({ priority: e.target.value as Task['priority'] })} aria-label="Priority">
            {PRIORITIES.map((p) => <option key={p} value={p}>{PRIORITY_LABEL[p]}</option>)}
          </Select>
        </Row>
        <Row label="Assignee">
          <div className="flex items-center gap-2">
            <Avatar user={task.assignee} size={22} />
            <Select value={task.assignee?.id ?? ''} onChange={(e) => save({ assigneeId: e.target.value || null })} aria-label="Assignee">
              <option value="">Unassigned</option>
              {project?.members.map((m) => <option key={m.id} value={m.id}>{m.displayName}</option>)}
            </Select>
          </div>
        </Row>
        <Row label="Reporter">
          <span className="flex items-center gap-2 text-[13px]"><Avatar user={task.reporter} size={22} />{task.reporter.displayName}</span>
        </Row>
        <Row label="Type">
          <Select value={task.type} onChange={(e) => save({ type: e.target.value as Task['type'] })} aria-label="Type">
            {TYPES.map((t) => <option key={t} value={t}>{TYPE_LABEL[t]}</option>)}
          </Select>
        </Row>
        <Row label="Start date">
          <Input type="date" value={task.startDate ?? ''} max={task.dueDate ?? undefined} onChange={(e) => save({ startDate: e.target.value || null })} aria-label="Start date" />
        </Row>
        <Row label="Due date">
          <Input type="date" value={task.dueDate ?? ''} min={task.startDate ?? undefined} onChange={(e) => save({ dueDate: e.target.value || null })} aria-label="Due date"
            className={cn(task.overdue && 'border-danger/60 text-danger')} />
        </Row>
        <Row label="Labels">
          <div className="flex flex-wrap items-center gap-1">
            {task.labels.map((l) => <LabelChip key={l.id} label={l} onRemove={() => toggleLabel(l.id)} />)}
            <Popover>
              <PopoverTrigger asChild>
                <button className="inline-flex h-5 items-center gap-0.5 rounded-full border border-dashed border-border px-1.5 text-[11px] text-muted-foreground hover:bg-muted" aria-label="Add label">
                  <Plus size={10} /> Add
                </button>
              </PopoverTrigger>
              <PopoverContent className="w-52">
                {labels?.map((l) => {
                  const on = task.labels.some((x) => x.id === l.id)
                  return (
                    <button key={l.id} onClick={() => toggleLabel(l.id)} className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-[13px] hover:bg-muted">
                      <span className="h-2.5 w-2.5 rounded-full" style={{ background: l.color }} />
                      <span className="flex-1">{l.name}</span>
                      {on && <Check size={13} />}
                    </button>
                  )
                })}
              </PopoverContent>
            </Popover>
          </div>
        </Row>
      </div>
      <div className="mt-3">
        <h3 className="mb-1 text-xs font-medium text-muted-foreground">Description</h3>
        <Textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          onBlur={() => description !== (task.description ?? '') && save({ description })}
          placeholder="Add a description…"
          className="min-h-[110px]"
          aria-label="Description"
        />
      </div>
    </div>
  )
}

function SectionTitle({ children, aside }: { children: React.ReactNode; aside?: React.ReactNode }) {
  return (
    <div className="mb-2 flex items-center justify-between">
      <h3 className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{children}</h3>
      {aside}
    </div>
  )
}

function AddRow({ placeholder, onAdd, label }: { placeholder: string; onAdd: (v: string) => Promise<unknown>; label: string }) {
  const [v, setV] = useState('')
  return (
    <form
      className="mt-2 flex gap-2"
      onSubmit={async (e) => {
        e.preventDefault()
        if (!v.trim()) return
        await onAdd(v.trim())
        setV('')
      }}
    >
      <Input value={v} onChange={(e) => setV(e.target.value)} placeholder={placeholder} aria-label={label} maxLength={255} />
      <Button type="submit" disabled={!v.trim()}>Add</Button>
    </form>
  )
}

function Checklist({ taskId, items }: { taskId: string; items: { id: string; text: string; done: boolean }[] }) {
  const r = useTaskSubResources(taskId)
  const done = items.filter((i) => i.done).length
  return (
    <section className="border-t border-border px-5 py-4">
      <SectionTitle aside={items.length > 0 && <span className="text-xs text-muted-foreground">{done}/{items.length}</span>}>Checklist</SectionTitle>
      {items.length > 0 && <ProgressBar percent={Math.round((100 * done) / items.length)} className="mb-2" />}
      <ul>
        {items.map((i) => (
          <li key={i.id} className="group flex items-center gap-2 rounded-md px-1 py-1 hover:bg-muted">
            <input type="checkbox" checked={i.done} onChange={(e) => r.updateChecklist.mutate({ id: i.id, done: e.target.checked })} aria-label={i.text} className="h-3.5 w-3.5 accent-[var(--primary)]" />
            <span className={cn('flex-1 text-[13px]', i.done && 'text-muted-foreground line-through')}>{i.text}</span>
            <button onClick={() => r.deleteChecklist.mutate(i.id)} className="opacity-0 group-hover:opacity-100" aria-label={`Delete ${i.text}`}><X size={13} /></button>
          </li>
        ))}
      </ul>
      <AddRow placeholder="Add a checklist item" label="New checklist item" onAdd={(t) => r.addChecklist.mutateAsync(t)} />
    </section>
  )
}

function Subtasks({ task, subtasks }: { task: Task; subtasks: Task[] }) {
  const r = useTaskSubResources(task.id)
  const move = useMoveTask()
  const { open } = useTaskPanel()
  if (task.parentId) return null
  const toggle = (s: Task) =>
    move.mutate({ task: s, status: s.status === 'DONE' ? 'TODO' : 'DONE' }, { onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Could not update subtask') })
  return (
    <section className="border-t border-border px-5 py-4">
      <SectionTitle aside={subtasks.length > 0 && <span className="text-xs text-muted-foreground">{subtasks.filter((s) => s.status === 'DONE').length}/{subtasks.length}</span>}>Subtasks</SectionTitle>
      <ul>
        {subtasks.map((s) => (
          <li key={s.id} className="flex items-center gap-2 rounded-md px-1 py-1 hover:bg-muted">
            <input type="checkbox" checked={s.status === 'DONE'} onChange={() => toggle(s)} aria-label={`Complete ${s.title}`} className="h-3.5 w-3.5 accent-[var(--primary)]" />
            <button onClick={() => open(s.id)} className={cn('flex-1 truncate text-left text-[13px] hover:underline', s.status === 'DONE' && 'text-muted-foreground line-through')}>{s.title}</button>
            <span className="text-xs text-muted-foreground">{s.identifier}</span>
          </li>
        ))}
      </ul>
      <AddRow placeholder="Add a subtask" label="New subtask" onAdd={(t) => r.addSubtask.mutateAsync(t)} />
    </section>
  )
}

function Attachments({ taskId, items }: { taskId: string; items: { id: string; fileName: string; sizeBytes: number; contentType: string; uploadedBy: User; createdAt: string }[] }) {
  const r = useTaskSubResources(taskId)
  const input = useRef<HTMLInputElement>(null)
  return (
    <section className="border-t border-border px-5 py-4">
      <SectionTitle aside={<Button size="sm" onClick={() => input.current?.click()}><Paperclip size={12} /> Attach</Button>}>Attachments</SectionTitle>
      <input
        ref={input}
        type="file"
        hidden
        onChange={(e) => {
          const f = e.target.files?.[0]
          if (f) r.addAttachment.mutate({ fileName: f.name, contentType: f.type || 'application/octet-stream', sizeBytes: f.size })
          e.target.value = ''
        }}
      />
      {items.length === 0 ? (
        <p className="text-[13px] text-muted-foreground">No files attached. Flowboard records file metadata only.</p>
      ) : (
        <ul className="space-y-1">
          {items.map((a) => (
            <li key={a.id} className="flex items-center gap-2 text-[13px]">
              <Paperclip size={13} className="text-muted-foreground" />
              <span className="flex-1 truncate">{a.fileName}</span>
              <span className="text-xs text-muted-foreground">{formatBytes(a.sizeBytes)} · {a.uploadedBy.displayName}</span>
              <button onClick={() => r.deleteAttachment.mutate(a.id)} aria-label={`Remove ${a.fileName}`}><X size={13} /></button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function Discussion({ task }: { task: Task }) {
  const [tab, setTab] = useState<'comments' | 'activity'>('comments')
  return (
    <section className="border-t border-border px-5 py-4">
      <div className="mb-3 flex gap-4 border-b border-border">
        {(['comments', 'activity'] as const).map((t) => (
          <button key={t} onClick={() => setTab(t)} className={cn('-mb-px border-b-2 pb-2 text-[13px] font-medium capitalize', tab === t ? 'border-primary text-foreground' : 'border-transparent text-muted-foreground')}>
            {t === 'comments' ? <span className="flex items-center gap-1"><MessageSquare size={13} /> Comments{task.commentCount ? ` (${task.commentCount})` : ''}</span> : 'Activity'}
          </button>
        ))}
      </div>
      {tab === 'comments' ? <Comments task={task} /> : <ActivityList taskId={task.id} />}
    </section>
  )
}

function Comments({ task }: { task: Task }) {
  const { data } = useTaskComments(task.id)
  const { data: project } = useProject(task.projectId)
  const r = useTaskSubResources(task.id)
  const confirm = useConfirm()
  const [body, setBody] = useState('')
  const [mentions, setMentions] = useState<User[]>([])
  const [query, setQuery] = useState<string | null>(null)
  const members = useMemo(() => project?.members ?? [], [project?.members])

  const suggestions = useMemo(
    () => (query === null ? [] : members.filter((m) => m.displayName.toLowerCase().includes(query.toLowerCase())).slice(0, 5)),
    [members, query],
  )

  const onChange = (v: string) => {
    setBody(v)
    const m = /(?:^|\s)@([\w ]{0,20})$/.exec(v)
    setQuery(m ? m[1] : null)
  }

  const pick = (u: User) => {
    setBody((b) => b.replace(/@([\w ]{0,20})$/, `@${u.displayName} `))
    setMentions((ms) => (ms.some((x) => x.id === u.id) ? ms : [...ms, u]))
    setQuery(null)
  }

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!body.trim()) return
    const ids = mentions.filter((u) => body.includes(`@${u.displayName}`)).map((u) => u.id)
    try {
      await r.addComment.mutateAsync({ body: body.trim(), mentionedUserIds: ids })
      setBody('')
      setMentions([])
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Could not post comment')
    }
  }

  const names = members.map((m) => m.displayName)
  const render = (text: string) => {
    if (!names.length) return text
    const re = new RegExp(`(@(?:${names.map((n) => n.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')}))`, 'g')
    return text.split(re).map((part, i) => (part.startsWith('@') && names.includes(part.slice(1)) ? <span key={i} className="rounded bg-primary-soft px-0.5 font-medium text-primary">{part}</span> : part))
  }

  return (
    <div>
      <ul className="space-y-4">
        {data?.items.length === 0 && <li className="text-[13px] text-muted-foreground">No comments yet. Start the conversation.</li>}
        {data?.items.map((c) => (
          <li key={c.id} className="group flex gap-2.5">
            <Avatar user={c.author} size={26} />
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2 text-xs">
                <span className="text-[13px] font-medium">{c.author.displayName}</span>
                <span className="text-muted-foreground">{timeAgo(c.createdAt)}</span>
                <button
                  className="ml-auto opacity-0 group-hover:opacity-100 text-muted-foreground hover:text-danger"
                  aria-label="Delete comment"
                  onClick={async () => {
                    if (await confirm({ title: 'Delete comment?', description: 'This cannot be undone.' })) r.deleteComment.mutate(c.id, { onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Could not delete') })
                  }}
                >
                  <Trash2 size={12} />
                </button>
              </div>
              <p className="whitespace-pre-wrap break-words text-[13px]">{render(c.body)}</p>
            </div>
          </li>
        ))}
      </ul>
      <form onSubmit={submit} className="relative mt-4">
        <Textarea value={body} onChange={(e) => onChange(e.target.value)} placeholder="Write a comment… use @ to mention a teammate" aria-label="Comment" maxLength={5000} />
        {suggestions.length > 0 && (
          <ul className="absolute bottom-full left-0 z-10 mb-1 w-56 rounded-lg border border-border bg-surface p-1 shadow-lg" role="listbox">
            {suggestions.map((u) => (
              <li key={u.id}>
                <button type="button" role="option" aria-selected={false} onMouseDown={(e) => { e.preventDefault(); pick(u) }} className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-[13px] hover:bg-muted">
                  <Avatar user={u} size={20} /> {u.displayName}
                </button>
              </li>
            ))}
          </ul>
        )}
        <div className="mt-2 flex justify-end">
          <Button type="submit" variant="primary" disabled={!body.trim() || r.addComment.isPending}>Comment</Button>
        </div>
      </form>
    </div>
  )
}

function ActivityList({ taskId }: { taskId: string }) {
  const { data } = useTaskActivity(taskId)
  if (!data) return <Skeleton className="h-16" />
  if (!data.items.length) return <p className="text-[13px] text-muted-foreground">No activity yet.</p>
  return (
    <ol className="space-y-3">
      {data.items.map((a) => (
        <li key={a.id} className="flex gap-2.5 text-[13px]">
          <Avatar user={a.actor} size={22} />
          <div>
            <span className="font-medium">{a.actor.displayName}</span> {a.summary}
            <div className="text-xs text-muted-foreground">{timeAgo(a.createdAt)}</div>
          </div>
        </li>
      ))}
    </ol>
  )
}
