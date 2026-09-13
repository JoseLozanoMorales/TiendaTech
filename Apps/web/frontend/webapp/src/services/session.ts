export interface SessionUser {
  usuarioId?: number
  id?: number
  usuario?: string
  nombre?: string
  rol?: string
  role?: string
  id_rol?: number
  idRol?: number
}

const USER_KEYS = ['tt_user', 'user', 'usuario', 'currentUser']
const LAST_ACTIVITY_KEY = 'tt_last_activity'
const TOKEN_KEYS = ['access', 'token', 'refreshToken']
export const SESSION_CLEARED_EVENT = 'tt-session-cleared'

// Elimina credenciales persistentes creadas por versiones anteriores. Los
// tokens de autenticacion nunca deben sobrevivir al cierre de la pestana.
;[...USER_KEYS, ...TOKEN_KEYS, 'usuarioId'].forEach((key) => localStorage.removeItem(key))
localStorage.removeItem(LAST_ACTIVITY_KEY)

export function getUser(): SessionUser | null {
  for (const key of USER_KEYS) {
    const raw = sessionStorage.getItem(key)
    if (!raw) continue
    try {
      const parsed = JSON.parse(raw)
      return parsed.user || parsed.data || parsed
    } catch {
      return null
    }
  }
  return null
}

export function saveSession(user: SessionUser, token?: string): void {
  sessionStorage.setItem('user', JSON.stringify(user))
  sessionStorage.setItem('tt_user', JSON.stringify(user))
  if (token) saveToken(token)
  markSessionActivity()
}

export function saveToken(value: string): void {
  sessionStorage.setItem('token', value)
  sessionStorage.setItem('access', value)
  localStorage.removeItem('token')
  localStorage.removeItem('access')
}

export function clearSession(): void {
  USER_KEYS.forEach((key) => {
    sessionStorage.removeItem(key)
    localStorage.removeItem(key)
  })
  ;[...TOKEN_KEYS, 'usuarioId', 'usuario'].forEach((key) => {
    sessionStorage.removeItem(key)
    localStorage.removeItem(key)
  })
  sessionStorage.removeItem(LAST_ACTIVITY_KEY)
  localStorage.removeItem(LAST_ACTIVITY_KEY)
  window.dispatchEvent(new Event(SESSION_CLEARED_EVENT))
}

export function markSessionActivity(): void {
  sessionStorage.setItem(LAST_ACTIVITY_KEY, String(Date.now()))
}

export function lastSessionActivity(): number {
  return Number(sessionStorage.getItem(LAST_ACTIVITY_KEY)) || Date.now()
}

export function token(): string {
  return sessionStorage.getItem('access') || sessionStorage.getItem('token') || ''
}

export function isAdmin(user: SessionUser | null): boolean {
  if (!user) return false
  return Number(user.id_rol ?? user.idRol ?? 0) === 1 ||
    String(user.rol ?? user.role ?? '').toLowerCase() === 'admin'
}
