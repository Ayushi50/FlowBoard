import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { Logo } from '@/components/Sidebar'
import { Spinner } from '@/components/ui/bits'
import { Button } from '@/components/ui/button'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import type { Role, Workspace } from '@/lib/types'

export function AcceptInvitePage() {
  const { token = '' } = useParams()
  const { user, loading } = useAuth()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { data, error, isLoading } = useQuery({
    queryKey: ['invite', token],
    retry: false,
    queryFn: () => api.get<{ workspaceName: string; email: string; role: Role }>(`/invitations/${token}`),
  })

  const accept = async () => {
    try {
      const ws = await api.post<Workspace>(`/invitations/${token}/accept`)
      await qc.invalidateQueries({ queryKey: ['workspaces'] })
      toast.success(`Joined ${ws.name}`)
      navigate(`/w/${ws.id}`)
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Could not accept invitation')
    }
  }

  return (
    <div className="grid min-h-full place-items-center p-4">
      <div className="w-full max-w-sm space-y-4 rounded-xl border border-border bg-surface p-6 text-center shadow-sm">
        <div className="flex justify-center"><Logo size={32} /></div>
        {(isLoading || loading) && <Spinner className="mx-auto" />}
        {error && (
          <>
            <h1 className="text-lg font-semibold">Invitation unavailable</h1>
            <p className="text-[13px] text-muted-foreground">{error instanceof ApiError ? error.message : 'This link is invalid.'}</p>
            <Button asChild><Link to="/">Go home</Link></Button>
          </>
        )}
        {data && !loading && (
          <>
            <h1 className="text-lg font-semibold">Join {data.workspaceName}</h1>
            <p className="text-[13px] text-muted-foreground">This invitation is for <strong>{data.email}</strong> as {data.role.toLowerCase()}.</p>
            {user ? (
              <Button variant="primary" size="lg" className="w-full" onClick={accept}>Accept invitation</Button>
            ) : (
              <div className="space-y-2">
                <Button variant="primary" size="lg" className="w-full" asChild><Link to={`/register?invite=${token}&email=${encodeURIComponent(data.email)}`}>Create account</Link></Button>
                <Button size="lg" className="w-full" asChild><Link to={`/login?invite=${token}&email=${encodeURIComponent(data.email)}`}>I already have an account</Link></Button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
