import { Copy, Mail, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Avatar } from '@/components/ui/avatar'
import { Card, Skeleton } from '@/components/ui/bits'
import { Button } from '@/components/ui/button'
import { useConfirm } from '@/components/ui/confirm'
import { Input, Select } from '@/components/ui/input'
import { useInvitations, useMembers, useWorkspaceMutations } from '@/hooks/queries'
import { ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import type { Role } from '@/lib/types'
import { formatDate } from '@/lib/utils'
import { useWorkspace } from '@/lib/workspace'

const ROLE_HELP: Record<Role, string> = {
  ADMIN: 'Manage workspace settings, members and all projects',
  MANAGER: 'Create projects and manage members of their projects',
  MEMBER: 'Work on tasks in projects they belong to',
}

export function TeamPage() {
  const { wsId, isAdmin } = useWorkspace()
  const { user } = useAuth()
  const { data: members, isLoading } = useMembers(wsId)
  const { data: invites } = useInvitations(wsId, isAdmin)
  const m = useWorkspaceMutations(wsId)
  const confirm = useConfirm()
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<Role>('MEMBER')

  const onError = (e: unknown) => toast.error(e instanceof ApiError ? e.message : 'Something went wrong')

  const invite = async (e: React.FormEvent) => {
    e.preventDefault()
    try {
      const inv = await m.invite.mutateAsync({ email, role })
      setEmail('')
      await navigator.clipboard?.writeText(`${location.origin}/invite/${inv.token}`).catch(() => undefined)
      toast.success('Invitation created — the link was copied to your clipboard')
    } catch (err) {
      onError(err)
    }
  }

  const remove = async (id: string, name: string, self: boolean) => {
    const ok = await confirm({
      title: self ? 'Leave this workspace?' : `Remove ${name}?`,
      description: self ? 'You will lose access to all of its projects.' : 'They lose access to every project in this workspace and their tasks become unassigned.',
      confirmLabel: self ? 'Leave' : 'Remove',
    })
    if (ok) m.removeMember.mutate(id, { onError, onSuccess: () => self && (location.href = '/') })
  }

  return (
    <div className="mx-auto max-w-4xl space-y-5 px-4 py-6 sm:px-6">
      <header><h1 className="text-xl font-semibold">Team</h1><p className="text-[13px] text-muted-foreground">{members?.length ?? '…'} people in this workspace.</p></header>
      {isAdmin && (
        <Card title="Invite someone">
          <form onSubmit={invite} className="flex flex-wrap gap-2">
            <Input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="teammate@company.com" className="min-w-[220px] flex-1" aria-label="Invitee email" />
            <Select value={role} onChange={(e) => setRole(e.target.value as Role)} className="w-32" aria-label="Role">
              <option value="MEMBER">Member</option><option value="MANAGER">Manager</option><option value="ADMIN">Admin</option>
            </Select>
            <Button type="submit" variant="primary" disabled={m.invite.isPending}><Mail size={13} /> Send invite</Button>
          </form>
          <p className="mt-2 text-xs text-muted-foreground">{ROLE_HELP[role]}. Flowboard does not send email; share the generated link with them.</p>
          {!!invites?.length && (
            <ul className="mt-4 divide-y divide-border border-t border-border">
              {invites.map((i) => (
                <li key={i.id} className="flex items-center gap-2 py-2 text-[13px]">
                  <span className="flex-1 truncate">{i.email} <span className="text-xs text-muted-foreground">· {i.role.toLowerCase()} · expires {formatDate(i.expiresAt)}</span></span>
                  <Button size="sm" onClick={() => navigator.clipboard.writeText(`${location.origin}/invite/${i.token}`).then(() => toast.success('Link copied'))}><Copy size={12} /> Copy link</Button>
                  <Button size="sm" variant="ghost" aria-label={`Revoke invitation for ${i.email}`} onClick={() => m.revoke.mutate(i.id, { onError })}><Trash2 size={12} /></Button>
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}
      {isLoading ? <Skeleton className="h-48" /> : (
        <ul className="divide-y divide-border rounded-xl border border-border bg-surface">
          {members?.map(({ user: u, role: r, joinedAt }) => (
            <li key={u.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
              <Avatar user={u} size={34} />
              <div className="min-w-0 flex-1 text-[13px]">
                <div className="font-medium">{u.displayName}{u.id === user?.id && <span className="ml-1.5 text-xs text-muted-foreground">(you)</span>}</div>
                <div className="truncate text-xs text-muted-foreground">{u.jobTitle ? `${u.jobTitle} · ` : ''}{u.email} · joined {formatDate(joinedAt)}</div>
              </div>
              {isAdmin ? (
                <Select value={r} className="w-28" aria-label={`Role for ${u.displayName}`} onChange={(e) => m.changeRole.mutate({ userId: u.id, role: e.target.value as Role }, { onError })}>
                  <option value="ADMIN">Admin</option><option value="MANAGER">Manager</option><option value="MEMBER">Member</option>
                </Select>
              ) : <span className="rounded-full bg-muted px-2 py-0.5 text-xs capitalize">{r.toLowerCase()}</span>}
              {(isAdmin || u.id === user?.id) && (
                <Button variant="ghost" size="icon" aria-label={u.id === user?.id ? 'Leave workspace' : `Remove ${u.displayName}`} onClick={() => remove(u.id, u.displayName, u.id === user?.id)}><Trash2 size={14} /></Button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
