import { FolderKanban, Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useSearch } from '@/hooks/queries'
import { useWorkspace } from '@/lib/workspace'
import { PriorityIcon, Spinner, StatusDot } from './ui/bits'
import { Dialog, DialogContent } from './ui/dialog'

export function SearchPalette({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const { wsId } = useWorkspace()
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const [debounced, setDebounced] = useState('')
  useEffect(() => {
    const t = setTimeout(() => setDebounced(q), 200)
    return () => clearTimeout(t)
  }, [q])
  useEffect(() => { if (!open) setQ('') }, [open])
  const { data, isFetching } = useSearch(wsId, debounced)

  const go = (path: string) => {
    onOpenChange(false)
    navigate(path)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent title="Search" description="Search projects and tasks" className="max-w-xl">
        <div className="flex items-center gap-2 border-b border-border px-4">
          <Search size={15} className="text-muted-foreground" />
          <input
            autoFocus
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search projects and tasks, or type WEB-12…"
            className="h-12 flex-1 bg-transparent text-[14px] outline-none placeholder:text-muted-foreground"
            aria-label="Search query"
          />
          {isFetching && <Spinner />}
        </div>
        <div className="scroll-thin max-h-[56vh] overflow-y-auto p-2">
          {debounced.trim().length < 2 && <p className="px-3 py-6 text-center text-[13px] text-muted-foreground">Type at least two characters.</p>}
          {data && !data.projects.length && !data.tasks.length && debounced.trim().length >= 2 && (
            <p className="px-3 py-6 text-center text-[13px] text-muted-foreground">No results for “{debounced}”.</p>
          )}
          {!!data?.projects.length && (
            <div className="mb-2">
              <div className="px-3 py-1 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">Projects</div>
              {data.projects.map((p) => (
                <button key={p.id} onClick={() => go(`/w/${wsId}/p/${p.id}/board`)} className="flex w-full items-center gap-2 rounded-md px-3 py-2 text-left hover:bg-muted">
                  <FolderKanban size={14} className="text-primary" />
                  <span className="flex-1 truncate">{p.name}</span>
                  <span className="text-xs text-muted-foreground">{p.key}</span>
                </button>
              ))}
            </div>
          )}
          {!!data?.tasks.length && (
            <div>
              <div className="px-3 py-1 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">Tasks</div>
              {data.tasks.map((t) => (
                <button key={t.id} onClick={() => go(`/w/${wsId}/p/${t.projectId}/board?task=${t.id}`)} className="flex w-full items-center gap-2 rounded-md px-3 py-2 text-left hover:bg-muted">
                  <StatusDot status={t.status} />
                  <span className="w-16 shrink-0 text-xs text-muted-foreground">{t.identifier}</span>
                  <span className="flex-1 truncate">{t.title}</span>
                  <PriorityIcon priority={t.priority} />
                </button>
              ))}
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
