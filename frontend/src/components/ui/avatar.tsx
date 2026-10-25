import type { User } from '@/lib/types'
import { cn, initials } from '@/lib/utils'

export function Avatar({ user, size = 24, className }: { user: Pick<User, 'displayName' | 'avatarColor'> | null; size?: number; className?: string }) {
  if (!user) {
    return (
      <span
        title="Unassigned"
        style={{ width: size, height: size }}
        className={cn('inline-flex shrink-0 items-center justify-center rounded-full border border-dashed border-muted-foreground/50 text-muted-foreground', className)}
      />
    )
  }
  return (
    <span
      title={user.displayName}
      aria-label={user.displayName}
      style={{ width: size, height: size, background: user.avatarColor, fontSize: Math.max(9, size * 0.4) }}
      className={cn('inline-flex shrink-0 select-none items-center justify-center rounded-full font-semibold text-white', className)}
    >
      {initials(user.displayName)}
    </span>
  )
}

export function AvatarStack({ users, max = 4, size = 22 }: { users: User[]; max?: number; size?: number }) {
  const shown = users.slice(0, max)
  return (
    <span className="inline-flex -space-x-1.5">
      {shown.map((u) => (
        <Avatar key={u.id} user={u} size={size} className="ring-2 ring-surface" />
      ))}
      {users.length > max && (
        <span style={{ width: size, height: size }} className="inline-flex items-center justify-center rounded-full bg-muted text-[10px] font-medium ring-2 ring-surface">
          +{users.length - max}
        </span>
      )}
    </span>
  )
}
