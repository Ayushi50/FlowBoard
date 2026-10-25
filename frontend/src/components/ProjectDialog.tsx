import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { useMembers, useProjectMutations } from '@/hooks/queries'
import { ApiError } from '@/lib/api'
import type { Project, ProjectStatus } from '@/lib/types'
import { PROJECT_STATUSES } from '@/lib/types'
import { useWorkspace } from '@/lib/workspace'
import { Avatar } from './ui/avatar'
import { Button } from './ui/button'
import { Dialog, DialogContent, DialogHeader } from './ui/dialog'
import { Field, Input, Select, Textarea } from './ui/input'

const suggestKey = (name: string) => {
  const words = name.trim().split(/\s+/).filter(Boolean)
  const k = words.length > 1 ? words.map((w) => w[0]).join('') : name.replace(/[^a-zA-Z0-9]/g, '').slice(0, 3)
  return k.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 5)
}

export function ProjectDialog({ open, onOpenChange, project }: { open: boolean; onOpenChange: (o: boolean) => void; project?: Project }) {
  const { wsId } = useWorkspace()
  const navigate = useNavigate()
  const { data: members } = useMembers(wsId)
  const m = useProjectMutations(wsId)
  const editing = !!project
  const [name, setName] = useState('')
  const [key, setKey] = useState('')
  const [keyTouched, setKeyTouched] = useState(false)
  const [description, setDescription] = useState('')
  const [status, setStatus] = useState<ProjectStatus>('ACTIVE')
  const [startDate, setStartDate] = useState('')
  const [targetDate, setTargetDate] = useState('')
  const [memberIds, setMemberIds] = useState<string[]>([])
  const [errors, setErrors] = useState<Record<string, string>>({})

  useEffect(() => {
    if (!open) return
    setName(project?.name ?? '')
    setKey(project?.key ?? '')
    setKeyTouched(!!project)
    setDescription(project?.description ?? '')
    setStatus(project?.status ?? 'ACTIVE')
    setStartDate(project?.startDate ?? '')
    setTargetDate(project?.targetDate ?? '')
    setMemberIds([])
    setErrors({})
  }, [open, project])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrors({})
    const body = { name, description, status, startDate: startDate || null, targetDate: targetDate || null }
    try {
      if (editing) {
        await m.update.mutateAsync({ id: project.id, ...body })
        toast.success('Project updated')
      } else {
        const created = await m.create.mutateAsync({ ...body, key, memberIds })
        toast.success(`Created ${created.name}`)
        navigate(`/w/${wsId}/p/${created.id}/board`)
      }
      onOpenChange(false)
    } catch (err) {
      if (err instanceof ApiError) {
        setErrors(err.fieldErrors ?? { form: err.message })
        if (!err.fieldErrors) toast.error(err.message)
      }
    }
  }

  const pending = m.create.isPending || m.update.isPending
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent title={editing ? 'Edit project' : 'New project'}>
        <DialogHeader title={editing ? 'Edit project' : 'New project'} description="Projects hold a board of tasks and a team." />
        <form onSubmit={submit} className="scroll-thin space-y-3 overflow-y-auto px-5 py-4">
          <div className="grid grid-cols-[1fr_96px] gap-3">
            <Field label="Name" error={errors.name}>
              <Input
                autoFocus
                required
                value={name}
                maxLength={120}
                onChange={(e) => {
                  setName(e.target.value)
                  if (!keyTouched && !editing) setKey(suggestKey(e.target.value))
                }}
                placeholder="Website redesign"
              />
            </Field>
            <Field label="Key" error={errors.key} hint={editing ? 'Fixed' : undefined}>
              <Input
                required
                value={key}
                disabled={editing}
                onChange={(e) => {
                  setKeyTouched(true)
                  setKey(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 10))
                }}
                placeholder="WEB"
              />
            </Field>
          </div>
          <Field label="Description" error={errors.description}>
            <Textarea value={description} onChange={(e) => setDescription(e.target.value)} placeholder="What is this project about?" />
          </Field>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <Field label="Status">
              <Select value={status} onChange={(e) => setStatus(e.target.value as ProjectStatus)}>
                {PROJECT_STATUSES.map((s) => <option key={s} value={s}>{s.replace('_', ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase())}</option>)}
              </Select>
            </Field>
            <Field label="Start date"><Input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} /></Field>
            <Field label="Target deadline"><Input type="date" value={targetDate} min={startDate || undefined} onChange={(e) => setTargetDate(e.target.value)} /></Field>
          </div>
          {!editing && members && members.length > 1 && (
            <fieldset>
              <legend className="mb-1 text-xs font-medium text-muted-foreground">Team members</legend>
              <div className="flex flex-wrap gap-1.5">
                {members.map(({ user }) => {
                  const on = memberIds.includes(user.id)
                  return (
                    <button
                      key={user.id}
                      type="button"
                      aria-pressed={on}
                      onClick={() => setMemberIds((ids) => (on ? ids.filter((i) => i !== user.id) : [...ids, user.id]))}
                      className={`flex items-center gap-1.5 rounded-full border py-0.5 pl-0.5 pr-2.5 text-xs ${on ? 'border-primary bg-primary-soft text-primary' : 'border-border hover:bg-muted'}`}
                    >
                      <Avatar user={user} size={20} /> {user.displayName}
                    </button>
                  )
                })}
              </div>
              <p className="mt-1 text-xs text-muted-foreground">You are added automatically as the project owner.</p>
            </fieldset>
          )}
          {errors.form && <p className="text-xs text-danger" role="alert">{errors.form}</p>}
          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" onClick={() => onOpenChange(false)}>Cancel</Button>
            <Button type="submit" variant="primary" disabled={pending || !name.trim() || (!editing && key.length < 2)}>
              {editing ? 'Save changes' : 'Create project'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
