import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, getToken, onUnauthorized, setToken } from './api'
import type { AuthResponse, User } from './types'

interface AuthState {
  user: User | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (input: { email: string; password: string; displayName: string; workspaceName?: string; invitationToken?: string }) => Promise<void>
  logout: () => void
  setUser: (u: User) => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient()
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(!!getToken())

  const logout = useCallback(() => {
    setToken(null)
    setUser(null)
    qc.clear()
  }, [qc])

  useEffect(() => {
    if (!getToken()) return
    api
      .get<User>('/users/me')
      .then(setUser)
      .catch(() => setToken(null))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => onUnauthorized(logout), [logout])

  const accept = (res: AuthResponse) => {
    setToken(res.token)
    qc.clear()
    setUser(res.user)
  }

  const value = useMemo<AuthState>(
    () => ({
      user,
      loading,
      setUser,
      logout,
      login: async (email, password) => accept(await api.post<AuthResponse>('/auth/login', { email, password })),
      register: async (input) => accept(await api.post<AuthResponse>('/auth/register', input)),
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [user, loading, logout],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth outside AuthProvider')
  return ctx
}
