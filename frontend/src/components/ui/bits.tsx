import { AlertTriangle, ArrowDown, ArrowUp, ChevronsUp, Equal, Minus, Loader2 } from 'lucide-react'
import type { ReactNode } from 'react'
import type { Label, TaskPriority, TaskStatus } from '@/lib/types'
import { cn, PRIORITY_COLOR, PRIORITY_LABEL, STATUS_DOT, STATUS_LABEL } from '@/lib/utils'

export function PriorityIcon({ priority, withLabel, size = 14 }: { priority: TaskPriority; withLabel?: boolean; size?: number }) {
  const Icon = { URGENT: AlertTriangle, HIGH: ChevronsUp, MEDIUM: Equal, LOW: ArrowDown, NONE: Minus }[priority]
  return (
    <span className={cn('inline-flex items-center gap-1', PRIORITY_COLOR[priority])} title={`Priority: ${PRIORITY_LABEL[priority]}`}>
      <Icon size={size} aria-hidden />
      {withLabel ? <span className="text-[13px] text-foreground">{PRIORITY_LABEL[priority]}</span> : <span className="sr-only">{PRIORITY_LABEL[priority]}</span>}
    </span>
  )
}

export function StatusDot({ status, withLabel }: { status: TaskStatus; withLabel?: boolean }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className={cn('h-2 w-2 rounded-full', STATUS_DOT[status])} aria-hidden />
      {withLabel ? <span>{STATUS_LABEL[status]}</span> : <span className="sr-only">{STATUS_LABEL[status]}</span>}
    </span>
  )
}

export function LabelChip({ label, onRemove }: { label: Label; onRemove?: () => void }) {
  return (
    <span
      className="inline-flex items-center gap-1 rounded-full px-2 py-px text-[11px] font-medium"
      style={{ background: `${label.color}22`, color: label.color }}
    >
      {label.name}
      {onRemove && (
        <button type="button" onClick={onRemove} aria-label={`Remove label ${label.name}`} className="opacity-70 hover:opacity-100">
          ×
        </button>
      )}
    </span>
  )
}

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={cn('animate-spin text-muted-foreground', className)} size={16} aria-label="Loading" />
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn('animate-pulse rounded-md bg-muted', className)} />
}

export function EmptyState({ icon, title, description, action }: { icon?: ReactNode; title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-border px-6 py-12 text-center">
      {icon && <div className="mb-3 rounded-full bg-primary-soft p-3 text-primary">{icon}</div>}
      <h3 className="text-[15px] font-semibold">{title}</h3>
      {description && <p className="mt-1 max-w-sm text-[13px] text-muted-foreground">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

export function Card({ title, action, children, className }: { title?: string; action?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={cn('rounded-xl border border-border bg-surface', className)}>
      {title && (
        <header className="flex items-center justify-between px-4 pt-3.5">
          <h3 className="text-[13px] font-semibold">{title}</h3>
          {action}
        </header>
      )}
      <div className="p-4">{children}</div>
    </section>
  )
}

export function ProgressBar({ percent, className }: { percent: number; className?: string }) {
  return (
    <div className={cn('h-1.5 w-full overflow-hidden rounded-full bg-muted', className)} role="progressbar" aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100}>
      <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${percent}%` }} />
    </div>
  )
}

export const SortIcon = ({ dir }: { dir?: 'asc' | 'desc' }) =>
  dir === 'asc' ? <ArrowUp size={12} /> : dir === 'desc' ? <ArrowDown size={12} /> : null
