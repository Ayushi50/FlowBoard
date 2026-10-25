import { clsx, type ClassValue } from 'clsx'
import { differenceInCalendarDays, format, formatDistanceToNowStrict, isToday, isTomorrow, parseISO } from 'date-fns'
import { twMerge } from 'tailwind-merge'
import type { TaskPriority, TaskStatus, TaskType } from './types'

export const cn = (...inputs: ClassValue[]) => twMerge(clsx(inputs))

export const STATUS_LABEL: Record<TaskStatus, string> = {
  BACKLOG: 'Backlog',
  TODO: 'To Do',
  IN_PROGRESS: 'In Progress',
  DONE: 'Done',
}

export const PRIORITY_LABEL: Record<TaskPriority, string> = {
  URGENT: 'Urgent',
  HIGH: 'High',
  MEDIUM: 'Medium',
  LOW: 'Low',
  NONE: 'No priority',
}

export const TYPE_LABEL: Record<TaskType, string> = {
  TASK: 'Task',
  BUG: 'Bug',
  FEATURE: 'Feature',
  IMPROVEMENT: 'Improvement',
}

export const STATUS_DOT: Record<TaskStatus, string> = {
  BACKLOG: 'bg-zinc-400',
  TODO: 'bg-sky-500',
  IN_PROGRESS: 'bg-amber-500',
  DONE: 'bg-emerald-500',
}

export const PRIORITY_COLOR: Record<TaskPriority, string> = {
  URGENT: 'text-red-600 dark:text-red-400',
  HIGH: 'text-orange-600 dark:text-orange-400',
  MEDIUM: 'text-amber-600 dark:text-amber-400',
  LOW: 'text-sky-600 dark:text-sky-400',
  NONE: 'text-muted-foreground',
}

export function initials(name: string): string {
  const parts = name.trim().split(/\s+/)
  return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase()
}

export function formatDue(date: string | null): string {
  if (!date) return ''
  const d = parseISO(date)
  if (isToday(d)) return 'Today'
  if (isTomorrow(d)) return 'Tomorrow'
  return format(d, differenceInCalendarDays(d, new Date()) > 300 || differenceInCalendarDays(d, new Date()) < -300 ? 'MMM d, yyyy' : 'MMM d')
}

export const formatDate = (iso: string | null) => (iso ? format(parseISO(iso), 'MMM d, yyyy') : '—')
export const timeAgo = (iso: string) => `${formatDistanceToNowStrict(parseISO(iso))} ago`
export const toISODate = (d: Date) => format(d, 'yyyy-MM-dd')

export function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(1)} MB`
}

export function greeting(now = new Date()): string {
  const h = now.getHours()
  return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening'
}
