import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react'
import { Button } from './button'
import { Dialog, DialogContent, DialogHeader } from './dialog'

interface ConfirmOptions {
  title: string
  description: string
  confirmLabel?: string
}
type ConfirmFn = (opts: ConfirmOptions) => Promise<boolean>

const ConfirmContext = createContext<ConfirmFn | null>(null)

/** Promise-based confirmation dialog used before every destructive action. */
export function ConfirmProvider({ children }: { children: ReactNode }) {
  const [opts, setOpts] = useState<ConfirmOptions | null>(null)
  const resolver = useRef<(v: boolean) => void>(() => {})

  const confirm = useCallback<ConfirmFn>((o) => {
    setOpts(o)
    return new Promise<boolean>((resolve) => {
      resolver.current = resolve
    })
  }, [])

  const close = (v: boolean) => {
    resolver.current(v)
    setOpts(null)
  }

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      <Dialog open={!!opts} onOpenChange={(o) => !o && close(false)}>
        <DialogContent title={opts?.title ?? 'Confirm'} description={opts?.description} className="max-w-md">
          <DialogHeader title={opts?.title ?? ''} description={opts?.description} />
          <div className="flex justify-end gap-2 px-5 py-4">
            <Button onClick={() => close(false)}>Cancel</Button>
            <Button variant="danger" onClick={() => close(true)} autoFocus>
              {opts?.confirmLabel ?? 'Delete'}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </ConfirmContext.Provider>
  )
}

export function useConfirm(): ConfirmFn {
  const ctx = useContext(ConfirmContext)
  if (!ctx) throw new Error('useConfirm outside ConfirmProvider')
  return ctx
}
