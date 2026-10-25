import type { Activity } from '@/lib/types'
import { timeAgo } from '@/lib/utils'
import { useTaskPanel } from '@/hooks/useTaskPanel'
import { Avatar } from './ui/avatar'

export function ActivityFeed({ items, empty = 'No activity yet.' }: { items: Activity[]; empty?: string }) {
  const { open } = useTaskPanel()
  if (!items.length) return <p className="text-[13px] text-muted-foreground">{empty}</p>
  return (
    <ol className="space-y-3">
      {items.map((a) => (
        <li key={a.id} className="flex gap-2.5 text-[13px]">
          <Avatar user={a.actor} size={22} />
          <div className="min-w-0">
            <span className="font-medium">{a.actor.displayName}</span>{' '}
            {a.taskId ? <button className="text-left hover:underline" onClick={() => open(a.taskId!)}>{a.summary}</button> : a.summary}
            <div className="text-xs text-muted-foreground">{timeAgo(a.createdAt)}</div>
          </div>
        </li>
      ))}
    </ol>
  )
}
