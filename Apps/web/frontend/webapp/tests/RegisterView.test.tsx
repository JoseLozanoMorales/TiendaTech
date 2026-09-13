import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RegisterView from '../src/views/RegisterView'
import { api } from '../src/services/api'

vi.mock('../src/services/api', () => ({ api: vi.fn() }))

const fillForm = async (user: ReturnType<typeof userEvent.setup>) => {
  await user.type(screen.getByLabelText('Nombre completo'), 'Nueva Cuenta')
  await user.type(screen.getByLabelText('Usuario'), 'nuevacuenta')
  await user.type(screen.getByLabelText('Correo'), 'nueva@tienda.com')
  await user.type(screen.getByLabelText('Teléfono'), '0999999999')
  await user.type(screen.getByLabelText('Cédula'), '1234567890')
  await user.type(screen.getByLabelText('Contraseña'), 'clave1234')
  await user.type(screen.getByLabelText('Repetir contraseña'), 'clave1234')
}

describe('Registro de cuenta', () => {
  beforeEach(() => {
    vi.mocked(api).mockReset()
  })

  it('rechaza el formulario si las contraseñas no coinciden', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter><RegisterView /></MemoryRouter>)
    await fillForm(user)
    await user.clear(screen.getByLabelText('Repetir contraseña'))
    await user.type(screen.getByLabelText('Repetir contraseña'), 'otraClave')

    await user.click(screen.getByRole('button', { name: 'Verificar correo' }))

    expect(await screen.findByText('Las contraseñas no coinciden.')).toBeInTheDocument()
    expect(api).not.toHaveBeenCalled()
  })

  it('rechaza cédula o teléfono que no tengan 10 dígitos', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter><RegisterView /></MemoryRouter>)
    await fillForm(user)
    await user.clear(screen.getByLabelText('Cédula'))
    await user.type(screen.getByLabelText('Cédula'), '123')

    await user.click(screen.getByRole('button', { name: 'Verificar correo' }))

    expect(await screen.findByText('Cédula y teléfono deben tener 10 dígitos.')).toBeInTheDocument()
  })

  it('envía el código OTP, lo verifica y crea la cuenta', async () => {
    vi.mocked(api).mockImplementation(async (path: string, options?: RequestInit) => {
      const body = options?.body ? JSON.parse(String(options.body)) : {}
      if (path === '/api/otp' && body.accion === 'enviar') return { txId: 'tx-1' }
      if (path === '/api/otp' && body.accion === 'validar') return {}
      if (path === '/api/usuarios/crear') return {}
      throw new Error(`ruta inesperada: ${path}`)
    })
    const user = userEvent.setup()
    render(<MemoryRouter><RegisterView /></MemoryRouter>)
    await fillForm(user)

    await user.click(screen.getByRole('button', { name: 'Verificar correo' }))

    expect(await screen.findByText('nueva@tienda.com')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Código'), '123456')
    await user.click(screen.getByRole('button', { name: 'Crear mi cuenta' }))

    await waitFor(() => expect(api).toHaveBeenCalledWith(
      '/api/usuarios/crear',
      expect.objectContaining({ method: 'POST' }),
    ))
    expect(await screen.findByText('Cuenta creada')).toBeInTheDocument()
  })
})
