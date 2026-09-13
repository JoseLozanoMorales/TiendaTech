import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AccountView from '../src/views/AccountView'
import { api } from '../src/services/api'
import { getUser } from '../src/services/session'

vi.mock('../src/services/api', () => ({ api: vi.fn() }))
vi.mock('../src/services/session', () => ({ getUser: vi.fn() }))

const profile = { nombre: 'Ada Cliente', usuario: 'ada', correo: 'ada@tienda.com', telefono: '0999999999', cedula: '1234567890' }
const addresses = [{ calle: 'Av. Siempre Viva 123', ciudadNombre: 'Quito', provinciaNombre: 'Pichincha' }]

const openAccount = () => render(<MemoryRouter><AccountView /></MemoryRouter>)

describe('Cuenta: perfil y contraseña', () => {
  beforeEach(() => {
    vi.mocked(getUser).mockReturnValue({ usuarioId: 7 })
    vi.mocked(api).mockImplementation(async (path: string) => {
      if (path === '/api/usuarios/me') return profile
      if (path.includes('/direcciones')) return addresses
      return {}
    })
  })

  it('muestra el perfil y las direcciones cargadas', async () => {
    openAccount()
    expect(await screen.findByText('Ada Cliente')).toBeInTheDocument()
    expect(screen.getByText('@ada')).toBeInTheDocument()
    expect(screen.getByText('Av. Siempre Viva 123')).toBeInTheDocument()
  })

  it('muestra el estado vacío cuando no hay direcciones', async () => {
    vi.mocked(api).mockImplementation(async (path: string) => {
      if (path === '/api/usuarios/me') return profile
      if (path.includes('/direcciones')) return []
      return {}
    })
    openAccount()
    expect(await screen.findByText('Todavía no tienes direcciones registradas.')).toBeInTheDocument()
  })

  it('actualiza la contraseña correctamente', async () => {
    const user = userEvent.setup()
    openAccount()
    await screen.findByText('Ada Cliente')

    await user.type(screen.getByLabelText('Contraseña actual'), 'actual123')
    await user.type(screen.getByLabelText('Nueva contraseña'), 'nuevaClave1')
    await user.type(screen.getByLabelText('Repetir contraseña'), 'nuevaClave1')
    await user.click(screen.getByRole('button', { name: 'Actualizar contraseña' }))

    await waitFor(() => expect(api).toHaveBeenCalledWith(
      '/api/seguridad/cambiar-password',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ actual: 'actual123', nueva: 'nuevaClave1' }) }),
    ))
    expect(await screen.findByText('Contraseña actualizada.')).toBeInTheDocument()
  })

  it('muestra un error si las contraseñas nuevas no coinciden', async () => {
    const user = userEvent.setup()
    openAccount()
    await screen.findByText('Ada Cliente')

    await user.type(screen.getByLabelText('Contraseña actual'), 'actual123')
    await user.type(screen.getByLabelText('Nueva contraseña'), 'nuevaClave1')
    await user.type(screen.getByLabelText('Repetir contraseña'), 'otraClave')
    await user.click(screen.getByRole('button', { name: 'Actualizar contraseña' }))

    expect(await screen.findByText('Las contraseñas nuevas no coinciden.')).toBeInTheDocument()
  })
})
