import { addDays, endOfWeek, startOfDay, startOfWeek, subDays } from 'date-fns'
import { useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import type { TaskFilters, TaskPriority, TaskStatus } from '@/lib/types'
import { toISODate } from '@/lib/utils'
import { useAuth } from '@/lib/auth'

export type DueFilter = '' | 'overdue' | 'today' | 'week' | 'next7'

const csv = (v: string | null) => (v ? v.split(',').filter(Boolean) : [])

/** Filters live in the URL so a filtered view is linkable and dashboard metrics can deep-link into it. */
export function useTaskFilters() {
  const [params, setParams] = useSearchParams()
  const { user } = useAuth()

  const state = useMemo(
    () => ({
      q: params.get('q') ?? '',
      assignee: params.get('assignee') ?? '',
      priority: csv(params.get('priority')) as TaskPriority[],
      status: csv(params.get('status')) as TaskStatus[],
      label: csv(params.get('label')),
      due: (params.get('due') ?? '') as DueFilter,
      completed: params.get('completed') ?? '',
      sort: params.get('sort') ?? '',
      dir: (params.get('dir') ?? 'asc') as 'asc' | 'desc',
    }),
    [params],
  )

  const set = useCallback(
    (patch: Record<string, string | string[] | null>) =>
      setParams(
        (p) => {
          const n = new URLSearchParams(p)
          for (const [k, v] of Object.entries(patch)) {
            const s = Array.isArray(v) ? v.join(',') : v
            if (!s) n.delete(k)
            else n.set(k, s)
          }
          n.delete('page')
          return n
        },
        { replace: true },
      ),
    [setParams],
  )

  const clear = useCallback(() => set({ q: null, assignee: null, priority: null, status: null, label: null, due: null, completed: null }), [set])

  const api = useMemo<TaskFilters>(() => {
    const today = new Date()
    const f: TaskFilters = {
      q: state.q || undefined,
      assigneeId: state.assignee === 'me' ? user?.id : state.assignee || undefined,
      priority: state.priority.length ? state.priority : undefined,
      status: state.status.length ? state.status : undefined,
      labelId: state.label.length ? state.label : undefined,
    }
    if (state.completed === 'week') f.completedSince = subDays(startOfDay(today), 7).toISOString()
    if (state.due === 'overdue') f.overdue = true
    if (state.due === 'today') { f.dueFrom = toISODate(today); f.dueTo = toISODate(today) }
    if (state.due === 'week') { f.dueFrom = toISODate(startOfWeek(today, { weekStartsOn: 1 })); f.dueTo = toISODate(endOfWeek(today, { weekStartsOn: 1 })) }
    if (state.due === 'next7') { f.dueFrom = toISODate(today); f.dueTo = toISODate(addDays(today, 7)) }
    return f
  }, [state, user?.id])

  const activeCount = [state.q, state.assignee, state.priority.length, state.status.length, state.label.length, state.due, state.completed].filter(Boolean).length
  return { state, set, clear, api, activeCount, params, setParams }
}
