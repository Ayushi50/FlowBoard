// Flowboard load test (k6). See loadtest/README.md for how to run it and docs/PERFORMANCE.md for recorded results.
//
//   k6 run -e BASE_URL=http://localhost:8080 --summary-export=results/summary.json loadtest/flowboard.k6.js
//
// Requires the backend to be started with SEED_LOAD_TEST=true (25 users, 20 projects, 30,000 tasks).
import http from 'k6/http'
import { check, group, sleep } from 'k6'
import { Counter, Rate } from 'k6/metrics'

const BASE = __ENV.BASE_URL || 'http://localhost:8080'
const API = `${BASE}/api/v1`
const PASSWORD = 'LoadTest123!'
const USERS = 25
const BROWSE_VUS = Number(__ENV.BROWSE_VUS || 50)
const WRITE_VUS = Number(__ENV.WRITE_VUS || 10)
const HOLD = __ENV.HOLD || '2m'

// 409 is an expected, meaningful answer for the write scenarios, so it is not counted as a failure there.
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 409))

const contentionWins = new Counter('contention_updates_applied')
const contentionConflicts = new Counter('contention_conflicts')
const unexpected = new Rate('unexpected_status')

export const options = {
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    browse: {
      executor: 'ramping-vus', exec: 'browse', startVUs: 0,
      stages: [
        { duration: '20s', target: BROWSE_VUS },
        { duration: HOLD, target: BROWSE_VUS },
        { duration: '10s', target: 0 },
      ],
    },
    writers: {
      executor: 'constant-vus', exec: 'writers', vus: WRITE_VUS, duration: HOLD, startTime: '20s',
    },
    contention: {
      executor: 'per-vu-iterations', exec: 'contention', vus: 20, iterations: 25, startTime: '10s', maxDuration: '3m',
    },
  },
  thresholds: {
    'http_req_failed{scenario:browse}': ['rate<0.01'],
    'http_req_duration{scenario:browse}': ['p(95)<800'],
    unexpected_status: ['rate<0.001'],
  },
}

// ------------------------------------------------------------------ helpers

const sessions = {}
function login(idx) {
  if (sessions[idx]) return sessions[idx]
  const res = http.post(`${API}/auth/login`, JSON.stringify({ email: `loaduser${idx}@flowboard.test`, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } })
  if (res.status !== 200) throw new Error(`login failed for loaduser${idx}: ${res.status}`)
  const token = res.json('token')
  const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }
  const ws = http.get(`${API}/workspaces`, { headers, tags: { name: 'workspaces' } }).json()[0].id
  const projects = http.get(`${API}/workspaces/${ws}/projects`, { headers, tags: { name: 'projects' } }).json().map((p) => p.id)
  return (sessions[idx] = { headers, ws, projects })
}
const me = () => login((__VU - 1) % USERS)
const pick = (arr) => arr[Math.floor(Math.random() * arr.length)]
const STATUSES = ['BACKLOG', 'TODO', 'IN_PROGRESS', 'DONE']

function get(s, path, name) {
  const res = http.get(`${API}${path}`, { headers: s.headers, tags: { name } })
  unexpected.add(res.status !== 200)
  return res
}

// ------------------------------------------------------------------ scenarios

export function browse() {
  const s = me()
  const project = pick(s.projects)
  const r = Math.random()
  if (r < 0.30) {
    group('board', () => check(get(s, `/projects/${project}/tasks?size=500`, 'board'), { 'board 200': (x) => x.status === 200 }))
  } else if (r < 0.50) {
    get(s, `/projects/${project}/tasks?size=25&page=${Math.floor(Math.random() * 60)}&sort=priority`, 'list_page')
  } else if (r < 0.65) {
    const q = pick(['login', 'billing', 'cache', 'export'])
    get(s, `/projects/${project}/tasks?status=IN_PROGRESS&status=TODO&q=${q}&size=25`, 'list_filtered')
  } else if (r < 0.75) {
    const list = get(s, `/projects/${project}/tasks?size=10&page=${Math.floor(Math.random() * 100)}`, 'list_small').json('items')
    if (list.length) get(s, `/tasks/${pick(list).id}`, 'task_detail')
  } else if (r < 0.85) {
    get(s, `/workspaces/${s.ws}/dashboard`, 'dashboard')
  } else if (r < 0.90) {
    get(s, `/workspaces/${s.ws}/tasks?mine=true&size=25&sort=dueDate`, 'my_tasks')
  } else if (r < 0.95) {
    get(s, `/workspaces/${s.ws}/search?q=${pick(['login', 'cache', 'Fix', 'P05-12'])}`, 'search')
  } else {
    get(s, `/workspaces/${s.ws}/notifications?size=20`, 'notifications')
  }
  sleep(0.2 + Math.random() * 0.6)
}

export function writers() {
  const s = me()
  const project = pick(s.projects)
  const items = get(s, `/projects/${project}/tasks?size=10&page=${Math.floor(Math.random() * 100)}`, 'list_small').json('items')
  if (!items.length) return
  const t = pick(items)
  const move = http.post(`${API}/tasks/${t.id}/move`,
    JSON.stringify({ status: pick(STATUSES), version: t.version }),
    { headers: s.headers, tags: { name: 'task_move' } })
  unexpected.add(![200, 409].includes(move.status))
  const edit = http.put(`${API}/tasks/${t.id}`,
    JSON.stringify({ title: t.title, description: t.description, priority: pick(['HIGH', 'MEDIUM', 'LOW']), type: t.type,
      assigneeId: t.assignee ? t.assignee.id : null, startDate: t.startDate, dueDate: t.dueDate, labelIds: t.labels.map((l) => l.id), version: t.version }),
    { headers: s.headers, tags: { name: 'task_update' } })
  unexpected.add(![200, 409].includes(edit.status))
  const c = http.post(`${API}/tasks/${t.id}/comments`, JSON.stringify({ body: 'load test comment', mentionedUserIds: [] }),
    { headers: s.headers, tags: { name: 'comment_create' } })
  unexpected.add(c.status !== 201)
  sleep(0.3)
}

// All contention VUs fight over ONE task, always sending the version they last read.
// Correctness check (teardown): task.version must equal the number of updates the server said it applied.
export function contention(data) {
  const s = me()
  const read = http.get(`${API}/tasks/${data.contendedTask}`, { headers: s.headers, tags: { name: 'contention_read' } })
  const t = read.json('task')
  const res = http.put(`${API}/tasks/${data.contendedTask}`,
    JSON.stringify({ title: `contended ${__VU}-${__ITER}`, description: t.description, priority: 'HIGH', type: t.type,
      assigneeId: null, startDate: null, dueDate: null, labelIds: [], version: t.version }),
    { headers: s.headers, tags: { name: 'contention_update' } })
  if (res.status === 200) contentionWins.add(1)
  else if (res.status === 409) contentionConflicts.add(1)
  unexpected.add(res.status !== 200 && res.status !== 409)
}

export function setup() {
  const s = login(0)
  const items = http.get(`${API}/projects/${s.projects[0]}/tasks?size=1`, { headers: s.headers }).json('items')
  const task = items[0]
  return { contendedTask: task.id, startVersion: task.version }
}

export function teardown(data) {
  const s = login(0)
  const t = http.get(`${API}/tasks/${data.contendedTask}`, { headers: s.headers }).json('task')
  console.log(`CONTENTION CHECK: task version moved ${data.startVersion} -> ${t.version} (= ${t.version - data.startVersion} applied updates; compare with contention_updates_applied)`)
}
