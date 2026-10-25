import * as DialogPrimitive from '@radix-ui/react-dialog'
import { X } from 'lucide-react'
import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

export const Dialog = DialogPrimitive.Root
export const DialogTrigger = DialogPrimitive.Trigger
export const DialogClose = DialogPrimitive.Close

export function DialogContent({
  title, description, children, className, side,
}: { title: string; description?: string; children: ReactNode; className?: string; side?: 'right' }) {
  return (
    <DialogPrimitive.Portal>
      <DialogPrimitive.Overlay className="anim-fade fixed inset-0 z-40 bg-black/40" />
      <DialogPrimitive.Content
        className={cn(
          'fixed z-50 flex flex-col border border-border bg-surface shadow-xl focus:outline-none',
          side === 'right'
            ? 'anim-slide inset-y-0 right-0 w-full max-w-[640px] border-y-0 border-r-0'
            : 'anim-pop left-1/2 top-[8vh] max-h-[84vh] w-[calc(100vw-24px)] max-w-lg -translate-x-1/2 rounded-xl',
          className,
        )}
      >
        <DialogPrimitive.Title className="sr-only">{title}</DialogPrimitive.Title>
        <DialogPrimitive.Description className="sr-only">{description ?? title}</DialogPrimitive.Description>
        {children}
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  )
}

export function DialogHeader({ title, description }: { title: string; description?: string }) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-border px-5 py-4">
      <div>
        <h2 className="text-[15px] font-semibold">{title}</h2>
        {description && <p className="mt-0.5 text-[13px] text-muted-foreground">{description}</p>}
      </div>
      <DialogPrimitive.Close className="rounded-md p-1 text-muted-foreground hover:bg-muted" aria-label="Close">
        <X size={16} />
      </DialogPrimitive.Close>
    </div>
  )
}
