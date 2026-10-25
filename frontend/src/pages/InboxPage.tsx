import { AtSign, Bell, CheckCheck, MessageSquare, Repeat, UserPlus, RefreshCw } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Avatar } from '@/components/ui/avatar'
import { EmptyState, Skeleton } from '@/components/ui/bits'
import { Button } from '@/components/ui/button'
import { useNotificationMutations, useNotifications } from '@/hooks/queries'
import type { AppNotification } from '@/lib/types'
import { cn, timeAgo } from '@/lib/utils'
import { useWorkspace } from '@/lib/workspace'

const ICON: Record<AppNotification['type'], React.ReactNode> = {
  ASSIGNED: <UserPlus size={14} />, MENTIONED: <AtSign size={14} />, COMMENTED: <MessageSquare size={14} />,
  STATUS_CHANGED: <Repeat size={14} />, TASK_UPDATED: <RefreshCw size={14} />,
}
const LABEL: Record<AppNotification['type'], string> = {
  ASSIGNED: 'Assigned', MENTIONED: 'Mention', COMMENTED: 'Comment', STATUS_CHANGED: 'Status', TASK_UPDATED: 'Update',
}

export function InboxPage() {
  const { wsId } = useWorkspace()
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [page, setPage] = useState(0)
  const { data, isLoading } = useNotifications(wsId, unreadOnly, page)
  const m = useNotificationMutations(wsId)
  const navigate = useNavigate()

  const open = (n: AppNotification) => {
    if (!n.read) m.markRead.mutate(n.id)
    if (n.taskId && n.projectId) navigate(`/w/${wsId}/p/${n.projectId}/board?task=${n.taskId}`)
  }

  return (
    <div className="mx-auto max-w-3xl space-y-4 px-4 py-6 sm:px-6">
      <header className="flex flex-wrap items-center gap-2">
        <div className="flex-1"><h1 className="text-xl font-semibold">Inbox</h1><p className="text-[13px] text-muted-foreground">Assignments, mentions and updates on your work.</p></div>
        <div className="flex rounded-md border border-border p-0.5 text-[13px]" role="tablist" aria-label="Filter notifications">
          {[false, true].map((u) => (
            <button key={String(u)} role="tab" aria-selected={unreadOnly === u} onClick={() => { setUnreadOnly(u); setPage(0) }}
              className={cn('rounded px-2.5 py-1', unreadOnly === u ? 'bg-primary-soft font-medium text-primary' : 'text-muted-foreground')}>{u ? 'Unread' : 'All'}</button>
          ))}
        </div>
        <Button onClick={() => m.markAllRead.mutate()} disabled={m.markAllRead.isPending}><CheckCheck size={14} /> Mark all as read</Button>
      </header>
      {isLoading || !data ? <Skeleton className="h-64" /> : data.items.length === 0 ? (
        <EmptyState icon={<Bell size={20} />} title={unreadOnly ? "You're all caught up" : 'No notifications yet'} description="You'll be notified when someone assigns you work, mentions you or updates your tasks." />
      ) : (
        <ul className="divide-y divide-border overflow-hidden rounded-xl border border-border bg-surface">
          {data.items.map((n) => (
            <li key={n.id}>
              <button onClick={() => open(n)} className={cn('flex w-full items-start gap-3 px-4 py-3 text-left hover:bg-muted/60', !n.read && 'bg-primary-soft/40')}>
                <span className="mt-0.5 flex items-center"><Avatar user={n.actor} size={28} /></span>
                <span className="min-w-0 flex-1">
                  <span className="block text-[13px]">{n.message}</span>
                  <span className="mt-0.5 flex items-center gap-1.5 text-xs text-muted-foreground">{ICON[n.type]} {LABEL[n.type]} · {timeAgo(n.createdAt)}</span>
                </span>
                {!n.read && <span className="mt-2 h-2 w-2 shrink-0 rounded-full bg-primary" aria-label="Unread" />}
              </button>
            </li>
          ))}
        </ul>
      )}
      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 text-xs text-muted-foreground">
          <Button size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Newer</Button>
          Page {page + 1} of {data.totalPages}
          <Button size="sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>Older</Button>
        </div>
      )}
    </div>
  )
}
