import { clearSession, getUser, saveToken, token } from './session'

// Shape comun de los endpoints de listado paginados (pedidos-service:
// PageResponse<T> -- content/page/size/totalElements/totalPages). Los
// servicios que consumen estos endpoints extraen `.content` y siguen
// devolviendo el array a quien los llama, para no propagar el cambio a
// todas las vistas.
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export class ApiError extends Error {
  constructor(message: string, public status: number) {
    super(message)
  }
}

interface ApiEnvelope<T> {
  status: number
  data: T
  message: string
  timestamp: string
}

function unwrapEnvelope<T>(body: unknown): T {
  if (body && typeof body === 'object' && 'status' in body && 'data' in body &&
      'message' in body && 'timestamp' in body) {
    return (body as ApiEnvelope<T>).data
  }
  return body as T
}

let refreshInFlight: Promise<string> | null = null

async function renewAccessToken(): Promise<string> {
  if (!refreshInFlight) {
    refreshInFlight = fetch('/auth/refresh', { method: 'POST', credentials: 'include' })
      .then(async (response) => {
        if (!response.ok) throw new ApiError('La sesión expiró.', response.status)
        const data = unwrapEnvelope<{ access?: string }>(await response.json())
        if (!data.access) throw new ApiError('No se recibió un nuevo token.', 401)
        saveToken(data.access)
        return data.access
      })
      .finally(() => { refreshInFlight = null })
  }
  return refreshInFlight
}

function requestHeaders(options: RequestInit): Headers {
  const headers = new Headers(options.headers)
  const jwt = token()
  addUserHeaders(headers)
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  if (jwt && jwt !== 'mock') headers.set('Authorization', `Bearer ${jwt}`)
  return headers
}

function addUserHeaders(headers: Headers): void {
  const user = getUser()
  const userId = user?.usuarioId ?? user?.id

  if (userId) {
    headers.set('X-User-Id', String(userId))
    headers.set('X-Usuario-Id', String(userId))
  }
  if (user?.usuario) headers.set('X-Usuario', user.usuario)
}

async function fetchResponse(path: string, options: RequestInit, headers: Headers): Promise<Response> {
  try {
    return await fetch(path, { ...options, headers, credentials: 'include' })
  } catch {
    throw new ApiError('No se pudo conectar con el servidor.', 0)
  }
}

async function refreshUnauthorized(path: string, options: RequestInit, headers: Headers, response: Response): Promise<Response> {
  if (response.status === 401 && path !== '/api/login' && path !== '/auth/refresh') {
    try {
      const renewedToken = await renewAccessToken()
      headers.set('Authorization', `Bearer ${renewedToken}`)
      response = await fetch(path, { ...options, headers, credentials: 'include' })
    } catch {
      clearSession()
    }
  }
  return response
}

function parseBody(text: string): unknown {
  if (!text) return null
  try { return JSON.parse(text) } catch { return text }
}

function responseError(body: unknown, status: number): ApiError {
  const data = body as { message?: string; error?: string } | null
  return new ApiError(data?.message || data?.error || String(body || `Error ${status}`), status)
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = requestHeaders(options)
  const initial = await fetchResponse(path, options, headers)
  const response = await refreshUnauthorized(path, options, headers, initial)
  if (response.status === 401) clearSession()
  const body = parseBody(await response.text())
  if (!response.ok) throw responseError(body, response.status)
  return unwrapEnvelope<T>(body)
}
