const TOKEN_KEY = 'flowboard.token'

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    /* storage unavailable */
  }
}

/** Structured error matching the backend's ApiError body. */
export class ApiError extends Error {
  status: number
  code: string
  fieldErrors?: Record<string, string>

  constructor(status: number, code: string, message: string, fieldErrors?: Record<string, string>) {
    super(message)
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }

  get isConflict() {
    return this.code === 'CONCURRENT_MODIFICATION'
  }
}

type Listener = () => void
const unauthorizedListeners = new Set<Listener>()
export const onUnauthorized = (fn: Listener) => {
  unauthorizedListeners.add(fn)
  return () => {
    unauthorizedListeners.delete(fn)
  }
}

type Query = Record<string, string | number | boolean | undefined | null | (string | number)[]>

export function qs(params?: Query): string {
  if (!params) return ''
  const sp = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '' || v === false) continue
    if (Array.isArray(v)) v.forEach((x) => sp.append(k, String(x)))
    else sp.set(k, String(v))
  }
  const s = sp.toString()
  return s ? `?${s}` : ''
}

export async function request<T>(
  method: string,
  path: string,
  opts: { body?: unknown; query?: Query; headers?: Record<string, string> } = {},
): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json', ...opts.headers }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json'

  let res: Response
  try {
    res = await fetch(`/api/v1${path}${qs(opts.query)}`, {
      method,
      headers,
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
    })
  } catch {
    throw new ApiError(0, 'NETWORK', 'Cannot reach the server. Check your connection.')
  }

  if (res.status === 204) return undefined as T
  const text = await res.text()
  const data = text ? safeJson(text) : undefined
  if (!res.ok) {
    const err = new ApiError(
      res.status,
      data?.code ?? 'ERROR',
      data?.message ?? `Request failed (${res.status})`,
      data?.fieldErrors,
    )
    if (res.status === 401 && token) unauthorizedListeners.forEach((fn) => fn())
    throw err
  }
  return data as T
}

function safeJson(text: string) {
  try {
    return JSON.parse(text)
  } catch {
    return undefined
  }
}

export const api = {
  get: <T>(path: string, query?: Query) => request<T>('GET', path, { query }),
  post: <T>(path: string, body?: unknown, headers?: Record<string, string>) =>
    request<T>('POST', path, { body: body ?? {}, headers }),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, { body: body ?? {} }),
  patch: <T>(path: string, body?: unknown) => request<T>('PATCH', path, { body: body ?? {} }),
  delete: <T = void>(path: string) => request<T>('DELETE', path),
}
