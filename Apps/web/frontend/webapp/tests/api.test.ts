import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError } from '../src/services/api'
import { getUser, saveSession, token } from '../src/services/session'

const fetchMock = vi.fn<typeof fetch>()
const json = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status })

beforeEach(() => vi.stubGlobal('fetch', fetchMock))
afterEach(() => vi.unstubAllGlobals())

describe('HTTP client after responsibility extraction', () => {
  it('sends identity and JSON headers and unwraps the API envelope', async () => {
    saveSession({ usuarioId: 7, usuario: 'ana' }, 'jwt')
    fetchMock.mockResolvedValue(json({ status: 200, data: { id: 8 }, message: 'OK', timestamp: 'now' }))
    await expect(api('/api/orders', { method: 'POST', body: '{}' })).resolves.toEqual({ id: 8 })
    const options = fetchMock.mock.calls[0][1]!
    expect(options.credentials).toBe('include')
    expect(Object.fromEntries(new Headers(options.headers))).toMatchObject({
      authorization: 'Bearer jwt', 'x-user-id': '7', 'x-usuario-id': '7',
      'x-usuario': 'ana', 'content-type': 'application/json',
    })
  })

  it('preserves multipart boundaries and custom headers', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
    await expect(api('/upload', { method: 'POST', body: new FormData(), headers: { 'X-Test': 'yes' } })).resolves.toBeNull()
    const headers = new Headers(fetchMock.mock.calls[0][1]!.headers)
    expect(headers.has('Content-Type')).toBe(false)
    expect(headers.get('X-Test')).toBe('yes')
  })

  it('shares token refresh across simultaneous unauthorized requests and retries both', async () => {
    saveSession({ id: 7 }, 'old')
    let finishRefresh!: (response: Response) => void
    const refresh = new Promise<Response>(resolve => { finishRefresh = resolve })
    fetchMock.mockImplementation(async (path, options) => {
      if (path === '/auth/refresh') return refresh
      if (new Headers(options?.headers).get('Authorization') === 'Bearer new') return json({ ok: true })
      return json({ message: 'expired' }, 401)
    })
    const requests = Promise.all([api('/first'), api('/second')])
    await vi.waitFor(() => expect(fetchMock.mock.calls.filter(([path]) => path === '/auth/refresh')).toHaveLength(1))
    finishRefresh(json({ access: 'new' }))
    await expect(requests).resolves.toEqual([{ ok: true }, { ok: true }])
    expect(token()).toBe('new')
    expect(fetchMock).toHaveBeenCalledTimes(5)
  })

  it('clears the session when refresh fails and preserves the original response error', async () => {
    saveSession({ id: 7 }, 'old')
    fetchMock.mockResolvedValueOnce(json({ message: 'expired' }, 401)).mockResolvedValueOnce(json({}, 401))
    await expect(api('/private')).rejects.toMatchObject({ status: 401, message: 'expired' })
    expect(getUser()).toBeNull()
    expect(token()).toBe('')
  })

  it('does not refresh a rejected login', async () => {
    fetchMock.mockResolvedValue(json({ error: 'invalid credentials' }, 401))
    await expect(api('/api/login')).rejects.toMatchObject({ status: 401, message: 'invalid credentials' })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('preserves plain-text errors and maps network failures', async () => {
    fetchMock.mockResolvedValueOnce(new Response('unavailable', { status: 503 }))
    await expect(api('/api/products')).rejects.toMatchObject({ status: 503, message: 'unavailable' })
    fetchMock.mockRejectedValueOnce(new TypeError('fetch failed'))
    await expect(api('/api/products')).rejects.toEqual(new ApiError('No se pudo conectar con el servidor.', 0))
  })
})
