import { addDays, format } from 'date-fns'
import { AlertCircle, CalendarClock, CheckCircle2, FolderPlus, ListTodo } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ActivityFeed } from '@/components/ActivityFeed'
import { ProjectDialog } from '@/components/ProjectDialog'
import { QuickTaskForm } from '@/components/QuickTaskDialog'
import { DueChip } from '@/components/TaskCard'
import { Avatar } from '@/components/ui/avatar'
import { Card, EmptyState, ProgressBar, Skeleton, PriorityIcon } from '@/components/ui/bits'
import { Button } from '@/components/ui/button'
import { useDashboard, useWorkspaceActivity } from '@/hooks/queries'
import { useTaskPanel } from '@/hooks/useTaskPanel'
import { useAuth } from '@/lib/auth'
import { cn, greeting } from '@/lib/utils'
import { useWorkspace } from '@/lib/workspace'

function Metric({ to, label, value, icon, tone }: { to: string; label: string; value: number; icon: React.ReactNode; tone?: string }) {
  return (
    <Link to={to} className="group rounded-xl border border-border bg-surface p-4 transition-colors hover:border-primary/50" aria-label={`${label}: ${value}`}>
      <div className="flex items-center justify-between text-muted-foreground">
        <span className="text-xs font-medium">{label}</span>
        {icon}
      </div>
      <div className={cn('mt-2 text-2xl font-semibold tabular-nums', tone)}>{value}</div>
    </Link>
  )
}

export function OverviewPage() {
  const { wsId, canCreateProject } = useWorkspace()
  const { user } = useAuth()
  const { data, isLoading } = useDashboard(wsId)
  const { data: activity } = useWorkspaceActivity(wsId)
  const { open } = useTaskPanel()
  const [creating, setCreating] = useState(false)

  return (
    <div className="mx-auto max-w-6xl space-y-5 px-4 py-6 sm:px-6">
      <header>
        <h1 className="text-xl font-semibold">{greeting()}, {user?.displayName.split(' ')[0]}</h1>
        <p className="text-[13px] text-muted-foreground">{format(new Date(), 'EEEE, MMMM d')}</p>
      </header>

      {isLoading || !data ? (
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-20" />)}</div>
      ) : !data.hasProjects ? (
        <EmptyState
          icon={<FolderPlus size={22} />}
          title="Create your first project"
          description={canCreateProject ? 'Projects hold a Kanban board, list and timeline for your team’s work. Start with one and invite your teammates.' : 'You have not been added to any projects yet. Ask a workspace manager or admin to add you.'}
          action={canCreateProject ? <Button variant="primary" onClick={() => setCreating(true)}>New project</Button> : undefined}
        />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Metric to={`/w/${wsId}/my-tasks`} label="Assigned to me" value={data.assignedToMe} icon={<ListTodo size={15} />} />
            <Metric to={`/w/${wsId}/my-tasks?due=today`} label="Due today" value={data.dueToday} icon={<CalendarClock size={15} />} />
            <Metric to={`/w/${wsId}/my-tasks?due=overdue`} label="Overdue" value={data.overdue} icon={<AlertCircle size={15} />} tone={data.overdue ? 'text-danger' : undefined} />
            <Metric to={`/w/${wsId}/my-tasks?completed=week`} label="Completed this week" value={data.completedThisWeek} icon={<CheckCircle2 size={15} />} tone="text-success" />
          </div>

          <div className="grid gap-4 lg:grid-cols-[1.4fr_1fr]">
            <div className="space-y-4">
              <Card title="Active projects" action={<Link className="text-xs text-primary hover:underline" to={`/w/${wsId}/projects`}>View all</Link>}>
                <ul className="space-y-3">
                  {data.activeProjects.slice(0, 6).map((p) => (
                    <li key={p.id}>
                      <Link to={`/w/${wsId}/p/${p.id}`} className="block rounded-lg p-1 hover:bg-muted">
                        <div className="mb-1 flex items-center justify-between text-[13px]">
                          <span className="font-medium">{p.name} <span className="text-xs font-normal text-muted-foreground">{p.key}</span></span>
                          <span className="tabular-nums text-muted-foreground">{p.completionPercent}% · {p.doneCount}/{p.taskCount}</span>
                        </div>
                        <ProgressBar percent={p.completionPercent} />
                      </Link>
                    </li>
                  ))}
                  {data.activeProjects.length === 0 && <li className="text-[13px] text-muted-foreground">No active projects.</li>}
                </ul>
              </Card>
              <Card title="Upcoming deadlines" action={<span className="text-xs text-muted-foreground">Next 14 days</span>}>
                {data.upcomingDeadlines.length === 0 ? (
                  <p className="text-[13px] text-muted-foreground">Nothing due before {format(addDays(new Date(), 14), 'MMM d')}.</p>
                ) : (
                  <ul className="divide-y divide-border">
                    {data.upcomingDeadlines.map((t) => (
                      <li key={t.id}>
                        <button onClick={() => open(t.id)} className="flex w-full items-center gap-2 py-2 text-left text-[13px] hover:bg-muted/60">
                          <PriorityIcon priority={t.priority} />
                          <span className="w-16 shrink-0 text-xs text-muted-foreground">{t.identifier}</span>
                          <span className="flex-1 truncate">{t.title}</span>
                          <DueChip task={t} />
                          <Avatar user={t.assignee} size={20} />
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </Card>
            </div>
            <div className="space-y-4">
              <Card title="Quick create"><QuickTaskForm /></Card>
              <Card title="Team activity">
                <ActivityFeed items={activity?.items ?? []} />
              </Card>
            </div>
          </div>
        </>
      )}
      <ProjectDialog open={creating} onOpenChange={setCreating} />
    </div>
  )
}
