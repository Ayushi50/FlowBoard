import { createContext, useContext } from 'react'
import type { Role, Workspace } from './types'

export interface WorkspaceCtx {
  workspace: Workspace
  wsId: string
  role: Role
  isAdmin: boolean
  canCreateProject: boolean
}

export const WorkspaceContext = createContext<WorkspaceCtx | null>(null)

export function useWorkspace(): WorkspaceCtx {
  const ctx = useContext(WorkspaceContext)
  if (!ctx) throw new Error('useWorkspace outside workspace route')
  return ctx
}
