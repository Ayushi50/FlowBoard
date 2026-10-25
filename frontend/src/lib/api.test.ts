import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, qs, setToken } from './api'

const respond = (status: number, body?: unknown) =>
  vi.fn().mockResolvedValue(new Response(body === undefined ? null : JSON.stringify(body), { status }))

afterEach(() => {
  vi.unstubAllGlobals()
  setToken(null)
})

describe('qs', () => {
  it('skips empty values and repeats array params', () => {
    expect(qs({ q: 'bug', page: 0, overdue: false, status: ['TODO', 'DONE'], empty: '', none: undefined })).toBe('?q=bug&page=0&status=TODO&status=DONE')
    expect(qs({})).toBe('')
  })
})

describe('request', () => {
  it('sends the bearer token and JSON body', async () => {
    const fetchMock = respond(200, { ok: true })
    vi.stubGlobal('fetch', fetchMock)
    setToken('abc')
    await api.post('/tasks', { title: 'x' }, { 'Idempotency-Key': 'k1' })
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/tasks')
    expect(init.headers.Authorization).toBe('Bearer abc')
    expect(init.headers['Idempotency-Key']).toBe('k1')
    expect(init.body).toBe('{"title":"x"}')
  })

  it('maps structured error bodies to ApiError, including conflicts', async () => {
    vi.stubGlobal('fetch', respond(409, { code: 'CONCURRENT_MODIFICATION', message: 'changed by someone else' }))
    const err = (await api.get('/tasks/1').catch((e) => e)) as ApiError
    expect(err).toBeInstanceOf(ApiError)
    expect(err.status).toBe(409)
    expect(err.isConflict).toBe(true)
    expect(err.message).toBe('changed by someone else')
  })

  it('exposes field errors for validation failures', async () => {
    vi.stubGlobal('fetch', respond(400, { code: 'VALIDATION_FAILED', message: 'bad', fieldErrors: { email: 'must be valid' } }))
    const err = (await api.post('/auth/register', {}).catch((e) => e)) as ApiError
    expect(err.fieldErrors).toEqual({ email: 'must be valid' })
  })

  it('returns undefined for 204 and a friendly error when offline', async () => {
    vi.stubGlobal('fetch', respond(204))
    await expect(api.delete('/tasks/1')).resolves.toBeUndefined()
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('fail')))
    await expect(api.get('/x')).rejects.toMatchObject({ code: 'NETWORK' })
  })
})
