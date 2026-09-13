import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LoginView from '../src/views/LoginView'
import { api } from '../src/services/api'
import { saveSession } from '../src/services/session'

vi.mock('../src/services/api', () => ({
  api: vi.fn(),
  ApiError: class ApiError extends Error {
    status: number
    constructor(message: string, status: number) { super(message); this.status = status }
  },
}))
vi.mock('../src/services/session', () => ({ saveSession: vi.fn() }))

const login = async (user: ReturnType<typeof userEvent.setup>) => {
  await user.type(screen.getByLabelText('Usuario'), 'ada')
  await user.type(screen.getByLabelText('Contraseña'), 'clave1234')
  await user.click(screen.getByRole('button', { name: 'Entrar' }))
}

describe('Inicio de sesión', () => {
  beforeEach(() => {
    vi.mocked(saveSession).mockReturnValue(undefined)
  })

  it('inicia sesión y redirige a /admin cuando el rol es administrador', async () => {
    vi.mocked(api).mockResolvedValue({ user: { usuarioId: 1, usuario: 'ada', idRol: 1 }, token: 'tok' })
    const onSessionChanged = vi.fn()
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/login']}><LoginView onSessionChanged={onSessionChanged} /></MemoryRouter>)

    await login(user)

    await waitFor(() => expect(api).toHaveBeenCalledWith('/api/login', expect.objectContaining({ method: 'POST' })))
    expect(saveSession).toHaveBeenCalledWith({ usuarioId: 1, usuario: 'ada', idRol: 1 }, 'tok')
    expect(onSessionChanged).toHaveBeenCalled()
  })

  it('muestra un mensaje de error si las credenciales son inválidas', async () => {
    vi.mocked(api).mockRejectedValue(new Error('Usuario o contraseña incorrectos.'))
    const user = userEvent.setup()
    render(<MemoryRouter><LoginView onSessionChanged={vi.fn()} /></MemoryRouter>)

    await login(user)

    expect(await screen.findByRole('alert')).toHaveTextContent('Usuario o contraseña incorrectos.')
    expect(saveSession).not.toHaveBeenCalled()
  })

  it('muestra el aviso de sesión cerrada por inactividad', () => {
    render(<MemoryRouter initialEntries={['/login?reason=inactive']}><LoginView onSessionChanged={vi.fn()} /></MemoryRouter>)
    expect(screen.getByRole('status')).toHaveTextContent('Tu sesión se cerró por inactividad.')
  })
})
