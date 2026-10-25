import { useEffect, useMemo, useState } from 'react'
import { toast } from 'sonner'
import { useCreateTask, useProjects } from '@/hooks/queries'
import { ApiError } from '@/lib/api'
import type { TaskPriority, TaskStatus } from '@/lib/types'
import { PRIORITIES } from '@/lib/types'
import { PRIORITY_LABEL } from '@/lib/utils'
import { useWorkspace } from '@/lib/workspace'
import { Button } from './ui/button'
import { Dialog, DialogContent, DialogHeader } from './ui/dialog'
import { Field, Input, Select } from './ui/input'

/** Compact create-task form. Used by the top bar "New" menu and the dashboard quick-create card. */
export function QuickTaskForm({ projectId: fixedProject, status, onDone, autoFocus }: { projectId?: string; status?: TaskStatus; onDone?: () => void; autoFocus?: boolean }) {
  const { wsId } = useWorkspace()
  const { data: projects } = useProjects(wsId)
  const writable = useMemo(() => projects?.filter((p) => !p.archived) ?? [], [projects])
  const [projectId, setProjectId] = useState(fixedProject ?? '')
  const [title, setTitle] = useState('')
  const [priority, setPriority] = useState<TaskPriority>('NONE')
  const [dueDate, setDueDate] = useState('')
  const create = useCreateTask(projectId)

  useEffect(() => {
    if (!projectId && writable.length) setProjectId(writable[0].id)
  }, [writable, projectId])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!title.trim() || !projectId) return
    try {
      const t = await create.mutateAsync({ title: title.trim(), priority, dueDate: dueDate || null, status })
      toast.success(`Created ${t.identifier}`)
      setTitle('')
      setDueDate('')
      onDone?.()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Could not create task')
    }
  }

  return (
    <form onSubmit={submit} className="space-y-3">
      <Field label="Title">
        <Input autoFocus={autoFocus} value={title} onChange={(e) => setTitle(e.target.value)} placeholder="What needs to be done?" maxLength={255} />
      </Field>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        {!fixedProject && (
          <Field label="Project">
            <Select value={projectId} onChange={(e) => setProjectId(e.target.value)} aria-label="Project">
              {writable.map((p) => <option key={p.id} value={p.id}>{p.key} · {p.name}</option>)}
            </Select>
          </Field>
        )}
        <Field label="Priority">
          <Select value={priority} onChange={(e) => setPriority(e.target.value as TaskPriority)}>
            {PRIORITIES.map((p) => <option key={p} value={p}>{PRIORITY_LABEL[p]}</option>)}
          </Select>
        </Field>
        <Field label="Due date"><Input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} /></Field>
      </div>
      <div className="flex justify-end">
        <Button type="submit" variant="primary" disabled={!title.trim() || !projectId || create.isPending}>Create task</Button>
      </div>
    </form>
  )
}

export function QuickTaskDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const { wsId } = useWorkspace()
  const { data: projects } = useProjects(wsId)
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent title="New task">
        <DialogHeader title="New task" />
        <div className="px-5 py-4">
          {projects && projects.filter((p) => !p.archived).length === 0 ? (
            <p className="text-[13px] text-muted-foreground">Create a project first — tasks live inside projects.</p>
          ) : (
            <QuickTaskForm autoFocus onDone={() => onOpenChange(false)} />
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
