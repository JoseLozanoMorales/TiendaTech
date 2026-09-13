import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RecoveryView from '../src/views/RecoveryView'
import { api } from '../src/services/api'

vi.mock('../src/services/api', () => ({ api: vi.fn() }))

describe('Recuperación de contraseña', () => {
  beforeEach(() => {
    vi.mocked(api).mockReset()
  })

  it('envía el correo y muestra la confirmación', async () => {
    vi.mocked(api).mockResolvedValue(undefined)
    const user = userEvent.setup()
    render(<MemoryRouter><RecoveryView /></MemoryRouter>)

    await user.type(screen.getByLabelText('Correo electrónico'), 'ada@tienda.com')
    await user.click(screen.getByRole('button', { name: 'Enviar contraseña temporal' }))

    expect(await screen.findByText('Revisa tu correo')).toBeInTheDocument()
    expect(api).toHaveBeenCalledWith('/api/usuarios/recuperar-password', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ correo: 'ada@tienda.com' }),
    }))
  })

  it('muestra un error si el envío falla', async () => {
    vi.mocked(api).mockRejectedValue(new Error('No se pudo enviar el correo.'))
    const user = userEvent.setup()
    render(<MemoryRouter><RecoveryView /></MemoryRouter>)

    await user.type(screen.getByLabelText('Correo electrónico'), 'ada@tienda.com')
    await user.click(screen.getByRole('button', { name: 'Enviar contraseña temporal' }))

    expect(await screen.findByText('No se pudo enviar el correo.')).toBeInTheDocument()
    expect(screen.queryByText('Revisa tu correo')).not.toBeInTheDocument()
  })
})
