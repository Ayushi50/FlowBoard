import { useState } from 'react'
import { Navigate, useSearchParams } from 'react-router-dom'
import { Logo } from '@/components/Sidebar'
import { Spinner } from '@/components/ui/bits'
import { Button } from '@/components/ui/button'
import { Field, Input } from '@/components/ui/input'
import { useWorkspaces } from '@/hooks/queries'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import type { Workspace } from '@/lib/types'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'

export function RootRedirect() {
  const { data, isLoading } = useWorkspaces()
  const [params] = useSearchParams()
  const { user, logout } = useAuth()
  const qc = useQueryClient()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  if (isLoading) return <div className="grid h-full place-items-center"><Spinner /></div>
  if (data?.length && !params.get('new')) {
    let last: string | null = null
    try { last = localStorage.getItem('flowboard.workspace') } catch { /* ignore */ }
    const target = data.find((w) => w.id === last) ?? data[0]
    return <Navigate to={`/w/${target.id}`} replace />
  }

  const create = async (e: React.FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      const ws = await api.post<Workspace>('/workspaces', { name })
      await qc.invalidateQueries({ queryKey: ['workspaces'] })
      navigate(`/w/${ws.id}`)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create workspace')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="grid h-full place-items-center p-4">
      <form onSubmit={create} className="w-full max-w-sm space-y-4 rounded-xl border border-border bg-surface p-6 shadow-sm">
        <Logo size={32} />
        <div>
          <h1 className="text-lg font-semibold">Create your workspace</h1>
          <p className="text-[13px] text-muted-foreground">A workspace is where your team's projects and tasks live{user ? `, ${user.displayName.split(' ')[0]}` : ''}.</p>
        </div>
        <Field label="Workspace name" error={error}>
          <Input autoFocus required value={name} onChange={(e) => setName(e.target.value)} placeholder="Acme Inc." maxLength={120} />
        </Field>
        <Button type="submit" variant="primary" size="lg" className="w-full" disabled={!name.trim() || busy}>Create workspace</Button>
        <div className="flex justify-between text-xs text-muted-foreground">
          {data?.length ? <a href="/" className="underline">Cancel</a> : <span>Have an invite? Open the invite link.</span>}
          <button type="button" className="underline" onClick={logout}>Log out</button>
        </div>
      </form>
    </div>
  )
}
