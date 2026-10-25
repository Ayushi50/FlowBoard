import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type {
  Activity, AppNotification, Dashboard, Invitation, Label, Member, Milestone, Page, Project, ProjectOverview,
  SearchResults, Task, TaskDetail, TaskFilters, TaskPriority, TaskStatus, TaskType, Workspace, Comment, ChecklistItem,
  ProjectStatus, Role, Attachment,
} from '@/lib/types'

// ---------------------------------------------------------------- workspaces & people

export const useWorkspaces = () => useQuery({ queryKey: ['workspaces'], queryFn: () => api.get<Workspace[]>('/workspaces') })

export const useMembers = (wsId: string) =>
  useQuery({ queryKey: ['members', wsId], queryFn: () => api.get<Member[]>(`/workspaces/${wsId}/members`) })

export const useInvitations = (wsId: string, enabled: boolean) =>
  useQuery({ queryKey: ['invitations', wsId], enabled, queryFn: () => api.get<Invitation[]>(`/workspaces/${wsId}/invitations`) })

export const useLabels = (wsId: string) =>
  useQuery({ queryKey: ['labels', wsId], queryFn: () => api.get<Label[]>(`/workspaces/${wsId}/labels`) })

// ---------------------------------------------------------------- projects

export const useProjects = (wsId: string, includeArchived = false) =>
  useQuery({
    queryKey: ['projects', wsId, includeArchived],
    queryFn: () => api.get<Project[]>(`/workspaces/${wsId}/projects`, { includeArchived }),
  })

export const useRecentProjects = (wsId: string) =>
  useQuery({ queryKey: ['projects-recent', wsId], queryFn: () => api.get<Project[]>(`/workspaces/${wsId}/projects/recent`, { limit: 4 }) })

export const useProject = (id: string) => useQuery({ queryKey: ['project', id], enabled: !!id, queryFn: () => api.get<Project>(`/projects/${id}`) })

export const useProjectOverview = (id: string) =>
  useQuery({ queryKey: ['project-overview', id], queryFn: () => api.get<ProjectOverview>(`/projects/${id}/overview`) })

export const useProjectActivity = (id: string) =>
  useQuery({ queryKey: ['activity', 'project', id], queryFn: () => api.get<Page<Activity>>(`/projects/${id}/activity`, { size: 15 }) })

export const useWorkspaceActivity = (wsId: string) =>
  useQuery({ queryKey: ['activity', 'workspace', wsId], queryFn: () => api.get<Page<Activity>>(`/workspaces/${wsId}/activity`, { size: 12 }) })

export const useMilestones = (projectId: string) =>
  useQuery({ queryKey: ['milestones', projectId], queryFn: () => api.get<Milestone[]>(`/projects/${projectId}/milestones`) })

export const useDashboard = (wsId: string) =>
  useQuery({ queryKey: ['dashboard', wsId], queryFn: () => api.get<Dashboard>(`/workspaces/${wsId}/dashboard`) })

export const useSearch = (wsId: string, q: string) =>
  useQuery({
    queryKey: ['search', wsId, q],
    enabled: q.trim().length >= 2,
    placeholderData: keepPreviousData,
    queryFn: () => api.get<SearchResults>(`/workspaces/${wsId}/search`, { q }),
  })

// ---------------------------------------------------------------- tasks

export interface TaskListParams extends TaskFilters {
  sort?: string
  dir?: 'asc' | 'desc'
  page?: number
  size?: number
}

function taskQuery(f: TaskListParams) {
  return {
    q: f.q, assigneeId: f.assigneeId, priority: f.priority, status: f.status, labelId: f.labelId,
    overdue: f.overdue, dueFrom: f.dueFrom, dueTo: f.dueTo, completedSince: f.completedSince, sort: f.sort, dir: f.dir, page: f.page, size: f.size,
  }
}

export const useProjectTasks = (projectId: string, params: TaskListParams) =>
  useQuery({
    queryKey: ['tasks', 'project', projectId, params],
    placeholderData: keepPreviousData,
    queryFn: () => api.get<Page<Task>>(`/projects/${projectId}/tasks`, taskQuery(params)),
  })

export const useWorkspaceTasks = (wsId: string, params: TaskListParams) =>
  useQuery({
    queryKey: ['tasks', 'workspace', wsId, params],
    placeholderData: keepPreviousData,
    queryFn: () =>
      api.get<Page<Task>>(`/workspaces/${wsId}/tasks`, { ...taskQuery(params), mine: params.mine, projectId: params.projectId }),
  })

export const useTaskDetail = (id: string | null) =>
  useQuery({ queryKey: ['task', id], enabled: !!id, queryFn: () => api.get<TaskDetail>(`/tasks/${id}`) })

export const useTaskComments = (id: string) =>
  useQuery({ queryKey: ['comments', id], queryFn: () => api.get<Page<Comment>>(`/tasks/${id}/comments`, { size: 100 }) })

export const useTaskActivity = (id: string) =>
  useQuery({ queryKey: ['activity', 'task', id], queryFn: () => api.get<Page<Activity>>(`/tasks/${id}/activity`, { size: 50 }) })

// ---------------------------------------------------------------- notifications

export const useNotifications = (wsId: string, unreadOnly: boolean, page: number) =>
  useQuery({
    queryKey: ['notifications', wsId, unreadOnly, page],
    placeholderData: keepPreviousData,
    queryFn: () => api.get<Page<AppNotification>>(`/workspaces/${wsId}/notifications`, { unreadOnly, page, size: 20 }),
  })

export const useUnreadCount = (wsId: string) =>
  useQuery({
    queryKey: ['unread-count', wsId],
    refetchInterval: 30_000,
    queryFn: () => api.get<{ count: number }>(`/workspaces/${wsId}/notifications/unread-count`),
  })

// ---------------------------------------------------------------- mutations

export interface TaskInput {
  title: string
  description?: string | null
  status?: TaskStatus
  priority?: TaskPriority
  type?: TaskType
  assigneeId?: string | null
  startDate?: string | null
  dueDate?: string | null
  labelIds?: string[]
}

export function useInvalidateWork() {
  const qc = useQueryClient()
  return () => {
    for (const key of ['tasks', 'task', 'projects', 'project', 'project-overview', 'dashboard', 'activity', 'comments', 'search', 'unread-count', 'notifications'])
      qc.invalidateQueries({ queryKey: [key] })
  }
}

export function useCreateTask(projectId: string) {
  const invalidate = useInvalidateWork()
  return useMutation({
    mutationFn: (input: TaskInput) => api.post<Task>(`/projects/${projectId}/tasks`, input, { 'Idempotency-Key': crypto.randomUUID() }),
    onSuccess: invalidate,
  })
}

// Edits to one task are applied strictly in order, each against the latest known version, so two quick
// edits by the same person never conflict with each other (only with other people's edits).
const saveChain = new Map<string, Promise<unknown>>()

export function useUpdateTask() {
  const qc = useQueryClient()
  const invalidate = useInvalidateWork()
  return useMutation({
    mutationFn: ({ task, patch }: { task: Task; patch: Partial<TaskInput> }) => {
      const run = async () => {
        const base = qc.getQueryData<TaskDetail>(['task', task.id])?.task ?? task
        const updated = await api.put<Task>(`/tasks/${task.id}`, {
          title: base.title,
          description: base.description,
          priority: base.priority,
          type: base.type,
          assigneeId: base.assignee?.id ?? null,
          startDate: base.startDate,
          dueDate: base.dueDate,
          labelIds: base.labels.map((l) => l.id),
          ...patch,
          version: base.version,
        })
        qc.setQueryData<TaskDetail>(['task', task.id], (d) => (d ? { ...d, task: updated } : d))
        return updated
      }
      const next = (saveChain.get(task.id) ?? Promise.resolve()).catch(() => undefined).then(run)
      saveChain.set(task.id, next)
      return next
    },
    onSuccess: invalidate,
    onError: invalidate,
  })
}

export interface MoveInput {
  task: Pick<Task, 'id' | 'version'>
  status: TaskStatus
  afterTaskId?: string | null
  beforeTaskId?: string | null
}

export function useMoveTask() {
  const invalidate = useInvalidateWork()
  return useMutation({
    mutationFn: ({ task, status, afterTaskId, beforeTaskId }: MoveInput) =>
      api.post<Task>(`/tasks/${task.id}/move`, { status, afterTaskId, beforeTaskId, version: task.version }),
    onSettled: invalidate,
  })
}

export function useDeleteTask() {
  const invalidate = useInvalidateWork()
  return useMutation({ mutationFn: (id: string) => api.delete(`/tasks/${id}`), onSuccess: invalidate })
}

export function useTaskSubResources(taskId: string) {
  const invalidate = useInvalidateWork()
  const opts = { onSuccess: invalidate }
  return {
    addSubtask: useMutation({ mutationFn: (title: string) => api.post<Task>(`/tasks/${taskId}/subtasks`, { title }), ...opts }),
    addChecklist: useMutation({ mutationFn: (text: string) => api.post<ChecklistItem>(`/tasks/${taskId}/checklist`, { text }), ...opts }),
    updateChecklist: useMutation({
      mutationFn: ({ id, done }: { id: string; done: boolean }) => api.patch<ChecklistItem>(`/checklist/${id}`, { done }),
      ...opts,
    }),
    deleteChecklist: useMutation({ mutationFn: (id: string) => api.delete(`/checklist/${id}`), ...opts }),
    addComment: useMutation({
      mutationFn: (v: { body: string; mentionedUserIds: string[] }) => api.post<Comment>(`/tasks/${taskId}/comments`, v),
      ...opts,
    }),
    deleteComment: useMutation({ mutationFn: (id: string) => api.delete(`/comments/${id}`), ...opts }),
    addAttachment: useMutation({
      mutationFn: (v: { fileName: string; contentType: string; sizeBytes: number }) => api.post<Attachment>(`/tasks/${taskId}/attachments`, v),
      ...opts,
    }),
    deleteAttachment: useMutation({ mutationFn: (id: string) => api.delete(`/attachments/${id}`), ...opts }),
  }
}

export interface ProjectInput {
  name: string
  key?: string
  description?: string
  status?: ProjectStatus
  startDate?: string | null
  targetDate?: string | null
  memberIds?: string[]
}

export function useProjectMutations(wsId: string) {
  const qc = useQueryClient()
  const refresh = () => {
    for (const key of ['projects', 'projects-recent', 'project', 'project-overview', 'dashboard', 'activity', 'milestones'])
      qc.invalidateQueries({ queryKey: [key] })
  }
  return {
    create: useMutation({ mutationFn: (v: ProjectInput) => api.post<Project>(`/workspaces/${wsId}/projects`, v), onSuccess: refresh }),
    update: useMutation({ mutationFn: ({ id, ...v }: ProjectInput & { id: string }) => api.put<Project>(`/projects/${id}`, v), onSuccess: refresh }),
    archive: useMutation({
      mutationFn: ({ id, archived }: { id: string; archived: boolean }) => api.post<Project>(`/projects/${id}/${archived ? 'archive' : 'unarchive'}`),
      onSuccess: refresh,
    }),
    remove: useMutation({ mutationFn: (id: string) => api.delete(`/projects/${id}`), onSuccess: refresh }),
    addMember: useMutation({
      mutationFn: ({ id, userId }: { id: string; userId: string }) => api.post<Project>(`/projects/${id}/members`, { userId }),
      onSuccess: refresh,
    }),
    removeMember: useMutation({
      mutationFn: ({ id, userId }: { id: string; userId: string }) => api.delete(`/projects/${id}/members/${userId}`),
      onSuccess: refresh,
    }),
    favorite: useMutation({
      mutationFn: ({ id, on }: { id: string; on: boolean }) => (on ? api.put(`/projects/${id}/favorite`) : api.delete(`/projects/${id}/favorite`)),
      onSuccess: refresh,
    }),
    addMilestone: useMutation({
      mutationFn: ({ id, name, dueDate }: { id: string; name: string; dueDate: string }) => api.post<Milestone>(`/projects/${id}/milestones`, { name, dueDate }),
      onSuccess: refresh,
    }),
    deleteMilestone: useMutation({ mutationFn: (id: string) => api.delete(`/milestones/${id}`), onSuccess: refresh }),
  }
}

export function useWorkspaceMutations(wsId: string) {
  const qc = useQueryClient()
  const refreshPeople = () => {
    qc.invalidateQueries({ queryKey: ['members', wsId] })
    qc.invalidateQueries({ queryKey: ['invitations', wsId] })
    qc.invalidateQueries({ queryKey: ['workspaces'] })
  }
  return {
    rename: useMutation({ mutationFn: (name: string) => api.patch<Workspace>(`/workspaces/${wsId}`, { name }), onSuccess: refreshPeople }),
    changeRole: useMutation({
      mutationFn: ({ userId, role }: { userId: string; role: Role }) => api.patch(`/workspaces/${wsId}/members/${userId}`, { role }),
      onSuccess: refreshPeople,
    }),
    removeMember: useMutation({ mutationFn: (userId: string) => api.delete(`/workspaces/${wsId}/members/${userId}`), onSuccess: refreshPeople }),
    invite: useMutation({
      mutationFn: (v: { email: string; role: Role }) => api.post<Invitation>(`/workspaces/${wsId}/invitations`, v),
      onSuccess: refreshPeople,
    }),
    revoke: useMutation({ mutationFn: (id: string) => api.delete(`/workspaces/${wsId}/invitations/${id}`), onSuccess: refreshPeople }),
    createLabel: useMutation({
      mutationFn: (v: { name: string; color: string }) => api.post<Label>(`/workspaces/${wsId}/labels`, v),
      onSuccess: () => qc.invalidateQueries({ queryKey: ['labels', wsId] }),
    }),
    deleteLabel: useMutation({
      mutationFn: (id: string) => api.delete(`/labels/${id}`),
      onSuccess: () => {
        qc.invalidateQueries({ queryKey: ['labels', wsId] })
        qc.invalidateQueries({ queryKey: ['tasks'] })
      },
    }),
  }
}

export function useNotificationMutations(wsId: string) {
  const qc = useQueryClient()
  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['notifications'] })
    qc.invalidateQueries({ queryKey: ['unread-count'] })
  }
  return {
    markRead: useMutation({ mutationFn: (id: string) => api.post(`/notifications/${id}/read`), onSuccess: refresh }),
    markAllRead: useMutation({ mutationFn: () => api.post(`/workspaces/${wsId}/notifications/read-all`), onSuccess: refresh }),
  }
}
