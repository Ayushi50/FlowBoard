export type Role = 'ADMIN' | 'MANAGER' | 'MEMBER'
export type TaskStatus = 'BACKLOG' | 'TODO' | 'IN_PROGRESS' | 'DONE'
export type TaskPriority = 'URGENT' | 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE'
export type TaskType = 'TASK' | 'BUG' | 'FEATURE' | 'IMPROVEMENT'
export type ProjectStatus = 'PLANNING' | 'ACTIVE' | 'ON_HOLD' | 'COMPLETED'

export const STATUSES: TaskStatus[] = ['BACKLOG', 'TODO', 'IN_PROGRESS', 'DONE']
export const PRIORITIES: TaskPriority[] = ['URGENT', 'HIGH', 'MEDIUM', 'LOW', 'NONE']
export const TYPES: TaskType[] = ['TASK', 'BUG', 'FEATURE', 'IMPROVEMENT']
export const PROJECT_STATUSES: ProjectStatus[] = ['PLANNING', 'ACTIVE', 'ON_HOLD', 'COMPLETED']

export interface User {
  id: string
  displayName: string
  email: string
  avatarColor: string
  jobTitle: string | null
}

export interface AuthResponse {
  token: string
  expiresInSeconds: number
  user: User
}

export interface Workspace {
  id: string
  name: string
  myRole: Role
  memberCount: number
}

export interface Member {
  user: User
  role: Role
  joinedAt: string
}

export interface Invitation {
  id: string
  email: string
  role: Role
  token: string
  status: string
  createdAt: string
  expiresAt: string
  emailSent: boolean
}

export interface Label {
  id: string
  name: string
  color: string
}

export interface Project {
  id: string
  workspaceId: string
  key: string
  name: string
  description: string | null
  owner: User
  status: ProjectStatus
  archived: boolean
  startDate: string | null
  targetDate: string | null
  taskCount: number
  doneCount: number
  completionPercent: number
  favorite: boolean
  members: User[]
  canManage: boolean
  version: number
  createdAt: string
  updatedAt: string
}

export interface Task {
  id: string
  projectId: string
  projectKey: string
  identifier: string
  number: number
  title: string
  description: string | null
  status: TaskStatus
  priority: TaskPriority
  type: TaskType
  assignee: User | null
  reporter: User
  parentId: string | null
  parentIdentifier: string | null
  startDate: string | null
  dueDate: string | null
  position: number
  version: number
  labels: Label[]
  commentCount: number
  attachmentCount: number
  subtaskTotal: number
  subtaskDone: number
  checklistTotal: number
  checklistDone: number
  overdue: boolean
  createdAt: string
  updatedAt: string
  completedAt: string | null
}

export interface ChecklistItem {
  id: string
  text: string
  done: boolean
  position: number
}

export interface Attachment {
  id: string
  fileName: string
  contentType: string
  sizeBytes: number
  uploadedBy: User
  createdAt: string
}

export interface TaskDetail {
  task: Task
  subtasks: Task[]
  checklist: ChecklistItem[]
  attachments: Attachment[]
}

export interface Comment {
  id: string
  author: User
  body: string
  createdAt: string
}

export interface Activity {
  id: string
  action: string
  summary: string
  oldValue: string | null
  newValue: string | null
  actor: User
  taskId: string | null
  projectId: string | null
  createdAt: string
}

export interface AppNotification {
  id: string
  type: 'ASSIGNED' | 'MENTIONED' | 'COMMENTED' | 'STATUS_CHANGED' | 'TASK_UPDATED'
  message: string
  actor: User | null
  taskId: string | null
  projectId: string | null
  read: boolean
  createdAt: string
}

export interface Milestone {
  id: string
  name: string
  dueDate: string
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface Dashboard {
  assignedToMe: number
  dueToday: number
  overdue: number
  completedThisWeek: number
  activeProjects: Project[]
  upcomingDeadlines: Task[]
  hasProjects: boolean
}

export interface ProjectOverview {
  project: Project
  tasksByStatus: Record<TaskStatus, number>
  overdueCount: number
  completedThisWeek: number
  upcomingDeadlines: Task[]
  milestones: Milestone[]
}

export interface SearchResults {
  projects: Project[]
  tasks: Task[]
}

export interface TaskFilters {
  q?: string
  assigneeId?: string
  priority?: TaskPriority[]
  status?: TaskStatus[]
  labelId?: string[]
  overdue?: boolean
  dueFrom?: string
  dueTo?: string
  mine?: boolean
  projectId?: string
  completedSince?: string
}
