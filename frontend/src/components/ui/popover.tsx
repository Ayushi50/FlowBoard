import * as P from '@radix-ui/react-popover'
import type { ComponentProps } from 'react'
import { cn } from '@/lib/utils'

export const Popover = P.Root
export const PopoverTrigger = P.Trigger
export function PopoverContent({ className, align = 'start', ...p }: ComponentProps<typeof P.Content>) {
  return (
    <P.Portal>
      <P.Content align={align} sideOffset={6} className={cn('anim-pop z-50 rounded-lg border border-border bg-surface p-2 shadow-lg', className)} {...p} />
    </P.Portal>
  )
}
