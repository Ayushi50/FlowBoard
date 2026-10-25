import { Trash2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Avatar } from '@/components/ui/avatar'
import { Card, LabelChip } from '@/components/ui/bits'
import { Button } from '@/components/ui/button'
import { useConfirm } from '@/components/ui/confirm'
import { Field, Input } from '@/components/ui/input'
import { useLabels, useWorkspaceMutations } from '@/hooks/queries'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { useTheme } from '@/lib/theme'
import type { User } from '@/lib/types'
import { useWorkspace } from '@/lib/workspace'

const COLORS = ['#6366f1', '#8b5cf6', '#ec4899', '#ef4444', '#f59e0b', '#10b981', '#0ea5e9', '#14b8a6']

export function SettingsPage() {
  const { user, setUser } = useAuth()
  const { wsId, workspace, isAdmin, role } = useWorkspace()
  const { data: labels } = useLabels(wsId)
  const m = useWorkspaceMutations(wsId)
  const { theme, setTheme } = useTheme()
  const confirm = useConfirm()
  const [name, setName] = useState(user?.displayName ?? '')
  const [title, setTitle] = useState(user?.jobTitle ?? '')
  const [color, setColor] = useState(user?.avatarColor ?? COLORS[0])
  const [wsName, setWsName] = useState(workspace.name)
  const [labelName, setLabelName] = useState('')
  const [labelColor, setLabelColor] = useState(COLORS[0])
  const [errors, setErrors] = useState<Record<string, string>>({})
  useEffect(() => setWsName(workspace.name), [workspace.name])

  const saveProfile = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrors({})
    try {
      setUser(await api.patch<User>('/users/me', { displayName: name, jobTitle: title, avatarColor: color }))
      toast.success('Profile saved')
    } catch (err) {
      if (err instanceof ApiError) setErrors(err.fieldErrors ?? {})
      toast.error(err instanceof ApiError ? err.message : 'Could not save profile')
    }
  }
  const onError = (e: unknown) => toast.error(e instanceof ApiError ? e.message : 'Something went wrong')

  return (
    <div className="mx-auto max-w-3xl space-y-5 px-4 py-6 sm:px-6">
      <h1 className="text-xl font-semibold">Settings</h1>
      <Card title="Profile">
        <form onSubmit={saveProfile} className="space-y-3">
          <div className="flex items-center gap-3"><Avatar user={{ displayName: name || '?', avatarColor: color }} size={44} />
            <div className="flex gap-1.5" role="radiogroup" aria-label="Avatar color">
              {COLORS.map((c) => <button type="button" key={c} role="radio" aria-checked={c === color} aria-label={c} onClick={() => setColor(c)} className={`h-5 w-5 rounded-full ring-offset-2 ring-offset-surface ${c === color ? 'ring-2 ring-foreground' : ''}`} style={{ background: c }} />)}
            </div>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Name" error={errors.displayName}><Input value={name} onChange={(e) => setName(e.target.value)} required /></Field>
            <Field label="Job title"><Input value={title} onChange={(e) => setTitle(e.target.value)} /></Field>
          </div>
          <Field label="Email"><Input value={user?.email ?? ''} disabled /></Field>
          <Button type="submit" variant="primary">Save profile</Button>
        </form>
      </Card>
      <Card title="Appearance">
        <div className="flex gap-2">{(['light', 'dark'] as const).map((t) => <Button key={t} variant={theme === t ? 'primary' : 'secondary'} onClick={() => setTheme(t)} className="capitalize">{t}</Button>)}</div>
      </Card>
      <Card title="Workspace">
        <form className="flex gap-2" onSubmit={(e) => { e.preventDefault(); m.rename.mutate(wsName, { onError, onSuccess: () => toast.success('Workspace renamed') }) }}>
          <Input value={wsName} onChange={(e) => setWsName(e.target.value)} disabled={!isAdmin} aria-label="Workspace name" />
          {isAdmin && <Button type="submit" variant="primary" disabled={!wsName.trim() || wsName === workspace.name}>Rename</Button>}
        </form>
        <p className="mt-2 text-xs text-muted-foreground">Your role: <strong className="capitalize">{role.toLowerCase()}</strong>{!isAdmin && ' — only admins can change workspace settings.'}</p>
      </Card>
      <Card title="Labels">
        <div className="flex flex-wrap gap-2">
          {labels?.map((l) => (
            <span key={l.id} className="inline-flex items-center gap-1">
              <LabelChip label={l} />
              {role !== 'MEMBER' && (
                <button aria-label={`Delete label ${l.name}`} className="text-muted-foreground hover:text-danger"
                  onClick={async () => (await confirm({ title: `Delete label “${l.name}”?`, description: 'It is removed from every task that uses it.' })) && m.deleteLabel.mutate(l.id, { onError })}><Trash2 size={12} /></button>
              )}
            </span>
          ))}
        </div>
        {role !== 'MEMBER' && (
          <form className="mt-3 flex flex-wrap items-center gap-2" onSubmit={(e) => { e.preventDefault(); m.createLabel.mutate({ name: labelName.trim(), color: labelColor }, { onError, onSuccess: () => setLabelName('') }) }}>
            <Input value={labelName} onChange={(e) => setLabelName(e.target.value)} placeholder="New label" className="w-40" maxLength={40} aria-label="New label name" />
            <div className="flex gap-1">{COLORS.map((c) => <button type="button" key={c} aria-label={`Label color ${c}`} aria-pressed={c === labelColor} onClick={() => setLabelColor(c)} className={`h-5 w-5 rounded-full ${c === labelColor ? 'ring-2 ring-foreground ring-offset-2 ring-offset-surface' : ''}`} style={{ background: c }} />)}</div>
            <Button type="submit" disabled={!labelName.trim()}>Add label</Button>
          </form>
        )}
      </Card>
    </div>
  )
}
