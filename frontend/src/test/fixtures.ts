import type { Task, User } from '@/lib/types'

export const user = (over: Partial<User> = {}): User => ({
  id: 'u1', displayName: 'Ava Chen', email: 'ava@test.dev', avatarColor: '#6366f1', jobTitle: null, ...over,
})

export const task = (over: Partial<Task> = {}): Task => ({
  id: 't1', projectId: 'p1', projectKey: 'WEB', identifier: 'WEB-1', number: 1, title: 'Write docs',
  description: null, status: 'TODO', priority: 'MEDIUM', type: 'TASK', assignee: user(), reporter: user(),
  parentId: null, parentIdentifier: null, startDate: null, dueDate: null, position: 1024, version: 0, labels: [],
  commentCount: 0, attachmentCount: 0, subtaskTotal: 0, subtaskDone: 0, checklistTotal: 0, checklistDone: 0,
  overdue: false, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z', completedAt: null, ...over,
})
