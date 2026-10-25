import { format, parseISO } from 'date-fns'
import { Plus, X } from 'lucide-react'
import { toast } from 'sonner'
import { ActivityFeed } from '@/components/ActivityFeed'
import { DueChip } from '@/components/TaskCard'
import { Avatar } from '@/components/ui/avatar'
import { Card, PriorityIcon, ProgressBar, Skeleton, StatusDot } from '@/components/ui/bits'
import { useConfirm } from '@/components/ui/confirm'
import { Menu, MenuContent, MenuItem, MenuTrigger } from '@/components/ui/dropdown'
import { Button } from '@/components/ui/button'
import { useMembers, useProjectActivity, useProjectMutations, useProjectOverview } from '@/hooks/queries'
import { useCurrentProject } from '@/hooks/useProjectOutlet'
import { useTaskPanel } from '@/hooks/useTaskPanel'
import { ApiError } from '@/lib/api'
import { STATUSES } from '@/lib/types'
import { formatDate, STATUS_LABEL } from '@/lib/utils'
import { useWorkspace } from '@/lib/workspace'

export function ProjectOverviewPage() {
  const project = useCurrentProject()
  const { wsId } = useWorkspace()
  const { data: overview } = useProjectOverview(project.id)
  const { data: activity } = useProjectActivity(project.id)
  const { data: wsMembers } = useMembers(wsId)
  const m = useProjectMutations(wsId)
  const confirm = useConfirm()
  const { open } = useTaskPanel()
  const addable = wsMembers?.filter((w) => !project.members.some((p) => p.id === w.user.id)) ?? []

  const removeMember = async (id: string, name: string) => {
    if (!(await confirm({ title: `Remove ${name}?`, description: 'They lose access to this project and their tasks here become unassigned.', confirmLabel: 'Remove' }))) return
    try {
      await m.removeMember.mutateAsync({ id: project.id, userId: id })
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Could not remove member')
    }
  }

  return (
    <div className="mx-auto max-w-6xl space-y-4 px-4 py-5 sm:px-6">
      <div className="grid gap-4 lg:grid-cols-[1.5fr_1fr]">
        <div className="space-y-4">
          <Card title="Summary">
            <p className="whitespace-pre-wrap text-[13px]">{project.description || <span className="text-muted-foreground">No description yet.</span>}</p>
            <dl className="mt-4 grid grid-cols-2 gap-x-6 gap-y-3 text-[13px] sm:grid-cols-4">
              {[
                ['Status', project.status.replace('_', ' ').toLowerCase()],
                ['Owner', project.owner.displayName],
                ['Start', formatDate(project.startDate)],
                ['Target', formatDate(project.targetDate)],
                ['Created', formatDate(project.createdAt)],
                ['Updated', formatDate(project.updatedAt)],
              ].map(([k, v]) => (
                <div key={k}><dt className="text-xs text-muted-foreground">{k}</dt><dd className="font-medium capitalize">{v}</dd></div>
              ))}
            </dl>
          </Card>
          <Card title="Progress">
            <div className="mb-1 flex items-end justify-between">
              <span className="text-2xl font-semibold tabular-nums">{project.completionPercent}%</span>
              <span className="text-xs text-muted-foreground">{project.doneCount} of {project.taskCount} tasks done</span>
            </div>
            <ProgressBar percent={project.completionPercent} />
            {overview ? (
              <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
                {STATUSES.map((s) => (
                  <div key={s} className="rounded-lg bg-surface-2 p-2.5">
                    <div className="flex items-center gap-1.5 text-xs text-muted-foreground"><StatusDot status={s} />{STATUS_LABEL[s]}</div>
                    <div className="mt-1 text-lg font-semibold tabular-nums">{overview.tasksByStatus[s]}</div>
                  </div>
                ))}
              </div>
            ) : <Skeleton className="mt-4 h-16" />}
            {overview && (
              <p className="mt-3 text-xs text-muted-foreground">
                <span className={overview.overdueCount ? 'font-medium text-danger' : ''}>{overview.overdueCount} overdue</span> · {overview.completedThisWeek} completed in the last 7 days
              </p>
            )}
          </Card>
          <Card title="Upcoming deadlines">
            {!overview?.upcomingDeadlines.length && !overview?.milestones.length && <p className="text-[13px] text-muted-foreground">Nothing due in the next 14 days.</p>}
            <ul className="divide-y divide-border">
              {overview?.milestones.filter((x) => x.dueDate >= format(new Date(), 'yyyy-MM-dd')).slice(0, 3).map((x) => (
                <li key={x.id} className="flex items-center gap-2 py-2 text-[13px]"><span className="h-2.5 w-2.5 rotate-45 bg-primary" /><span className="flex-1 font-medium">{x.name}</span><span className="text-xs text-muted-foreground">{format(parseISO(x.dueDate), 'MMM d')}</span></li>
              ))}
              {overview?.upcomingDeadlines.map((t) => (
                <li key={t.id}>
                  <button onClick={() => open(t.id)} className="flex w-full items-center gap-2 py-2 text-left text-[13px] hover:bg-muted/60">
                    <PriorityIcon priority={t.priority} /><span className="w-16 text-xs text-muted-foreground">{t.identifier}</span>
                    <span className="flex-1 truncate">{t.title}</span><DueChip task={t} /><Avatar user={t.assignee} size={20} />
                  </button>
                </li>
              ))}
            </ul>
          </Card>
        </div>
        <div className="space-y-4">
          <Card
            title={`Members (${project.members.length})`}
            action={
              project.canManage && addable.length > 0 && !project.archived ? (
                <Menu>
                  <MenuTrigger asChild><Button size="sm"><Plus size={12} /> Add</Button></MenuTrigger>
                  <MenuContent>
                    {addable.map(({ user }) => (
                      <MenuItem key={user.id} onSelect={() => m.addMember.mutate({ id: project.id, userId: user.id }, { onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Could not add member') })}>
                        <Avatar user={user} size={20} /> {user.displayName}
                      </MenuItem>
                    ))}
                  </MenuContent>
                </Menu>
              ) : undefined
            }
          >
            <ul className="space-y-2">
              {project.members.map((u) => (
                <li key={u.id} className="group flex items-center gap-2.5">
                  <Avatar user={u} size={28} />
                  <div className="min-w-0 flex-1 text-[13px]">
                    <div className="truncate font-medium">{u.displayName}{u.id === project.owner.id && <span className="ml-1.5 rounded bg-primary-soft px-1 text-[10px] text-primary">Owner</span>}</div>
                    <div className="truncate text-xs text-muted-foreground">{u.jobTitle ?? u.email}</div>
                  </div>
                  {project.canManage && u.id !== project.owner.id && (
                    <button onClick={() => removeMember(u.id, u.displayName)} className="opacity-0 group-hover:opacity-100" aria-label={`Remove ${u.displayName}`}><X size={14} /></button>
                  )}
                </li>
              ))}
            </ul>
          </Card>
          <Card title="Recent activity"><ActivityFeed items={activity?.items ?? []} /></Card>
        </div>
      </div>
    </div>
  )
}
