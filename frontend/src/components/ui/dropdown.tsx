import * as M from '@radix-ui/react-dropdown-menu'
import type { ComponentProps } from 'react'
import { cn } from '@/lib/utils'

export const Menu = M.Root
export const MenuTrigger = M.Trigger

export function MenuContent({ className, align = 'end', ...p }: ComponentProps<typeof M.Content>) {
  return (
    <M.Portal>
      <M.Content
        align={align}
        sideOffset={6}
        className={cn('anim-pop z-50 min-w-[180px] rounded-lg border border-border bg-surface p-1 shadow-lg', className)}
        {...p}
      />
    </M.Portal>
  )
}

export function MenuItem({ className, danger, ...p }: ComponentProps<typeof M.Item> & { danger?: boolean }) {
  return (
    <M.Item
      className={cn(
        'flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-[13px] outline-none data-[highlighted]:bg-muted data-[disabled]:opacity-50',
        danger && 'text-danger',
        className,
      )}
      {...p}
    />
  )
}

export const MenuSeparator = () => <M.Separator className="my-1 h-px bg-border" />
export const MenuLabel = ({ children }: { children: React.ReactNode }) => (
  <M.Label className="px-2 py-1 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">{children}</M.Label>
)
