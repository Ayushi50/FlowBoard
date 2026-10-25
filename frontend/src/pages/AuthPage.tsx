import { useState } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { Logo } from '@/components/Sidebar'
import { Button } from '@/components/ui/button'
import { Field, Input } from '@/components/ui/input'
import { ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'

export function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const { user, login, register } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const invite = params.get('invite') ?? undefined
  const [email, setEmail] = useState(params.get('email') ?? '')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [workspaceName, setWorkspaceName] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to="/" replace />

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setErrors({})
    try {
      if (mode === 'login') await login(email, password)
      else await register({ email, password, displayName, workspaceName: invite ? undefined : workspaceName || undefined, invitationToken: invite })
      navigate(invite && mode === 'login' ? `/invite/${invite}` : '/')
    } catch (err) {
      if (err instanceof ApiError) setErrors(err.fieldErrors ?? { form: err.message })
      else setErrors({ form: 'Something went wrong. Please try again.' })
    } finally {
      setBusy(false)
    }
  }

  const isLogin = mode === 'login'
  return (
    <div className="grid min-h-full place-items-center p-4">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex items-center gap-2"><Logo size={28} /><span className="text-lg font-semibold">Flowboard</span></div>
        <form onSubmit={submit} className="space-y-4 rounded-xl border border-border bg-surface p-6 shadow-sm" aria-label={isLogin ? 'Log in' : 'Sign up'}>
          <div>
            <h1 className="text-lg font-semibold">{isLogin ? 'Welcome back' : 'Create your account'}</h1>
            <p className="text-[13px] text-muted-foreground">{isLogin ? 'Log in to continue to your workspace.' : invite ? 'Sign up to join the workspace you were invited to.' : 'Plan work, ship together.'}</p>
          </div>
          {!isLogin && (
            <Field label="Full name" error={errors.displayName}>
              <Input required value={displayName} onChange={(e) => setDisplayName(e.target.value)} autoComplete="name" />
            </Field>
          )}
          <Field label="Email" error={errors.email}>
            <Input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
          </Field>
          <Field label="Password" error={errors.password} hint={isLogin ? undefined : 'At least 8 characters'}>
            <Input type="password" required value={password} onChange={(e) => setPassword(e.target.value)} autoComplete={isLogin ? 'current-password' : 'new-password'} />
          </Field>
          {!isLogin && !invite && (
            <Field label="Workspace name" hint="You can create more later" error={errors.workspaceName}>
              <Input value={workspaceName} onChange={(e) => setWorkspaceName(e.target.value)} placeholder="Acme Inc." />
            </Field>
          )}
          {errors.form && <p className="text-[13px] text-danger" role="alert">{errors.form}</p>}
          <Button type="submit" variant="primary" size="lg" className="w-full" disabled={busy}>{isLogin ? 'Log in' : 'Create account'}</Button>
        </form>
        <p className="mt-4 text-center text-[13px] text-muted-foreground">
          {isLogin ? "New to Flowboard? " : 'Already have an account? '}
          <Link className="font-medium text-primary hover:underline" to={`${isLogin ? '/register' : '/login'}${invite ? `?invite=${invite}` : ''}`}>
            {isLogin ? 'Create an account' : 'Log in'}
          </Link>
        </p>
      </div>
    </div>
  )
}
