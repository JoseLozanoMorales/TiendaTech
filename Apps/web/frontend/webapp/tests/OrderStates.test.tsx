import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminView from '../src/views/AdminView'
import { api } from '../src/services/api'
import { getUser } from '../src/services/session'

vi.mock('../src/services/api', () => ({ api: vi.fn() }))
vi.mock('../src/services/session', () => ({ getUser: vi.fn() }))

let orderState: string
beforeEach(() => {
  orderState = 'PENDIENTE'
  vi.mocked(getUser).mockReturnValue({ usuarioId: 1, nombre: 'Administrador' })
  // Simular el servicio; las reglas de botones y recarga son las del componente real.
  vi.mocked(api).mockImplementation(async (path, options) => {
    if (options?.method === 'POST') {
      orderState = path.endsWith('/enviar') ? 'ENVIADA' : 'CANCELADA'
      return {}
    }
    if (path.startsWith('/api/ordenes-compra')) {
      return [{ ordenCompraId: 42, numeroOrden: 'OC-42', estado: orderState, total: 50 }]
    }
    return []
  })
})

async function openOrders() {
  const user = userEvent.setup()
  render(<MemoryRouter><AdminView /></MemoryRouter>)
  await user.click(screen.getByRole('button', { name: /Órdenes a proveedores/ }))
  const cell = await screen.findByText('OC-42')
  return { user, row: within(cell.closest('tr')!) }
}

describe('Estados de órdenes de compra', () => {
  it.each([
    { state: 'PENDIENTE', actions: ['Enviar', 'Cancelar'] },
    { state: 'ENVIADA', actions: ['Cancelar', 'Recibir'] },
    { state: 'RECIBIDA_PARCIAL', actions: ['Recibir'] },
    { state: 'RECIBIDA', actions: [] },
    { state: 'CANCELADA', actions: [] },
  ])('ofrece únicamente las acciones permitidas en $state', async ({ state, actions }) => {
    orderState = state
    const { row } = await openOrders()
    expect(row.getByText(state)).toBeInTheDocument()
    for (const action of ['Enviar', 'Cancelar', 'Recibir']) {
      if (actions.includes(action)) {
        expect(row.getByRole('button', { name: action })).toBeEnabled()
      } else {
        expect(row.queryByRole('button', { name: action })).not.toBeInTheDocument()
      }
    }
  })

  it.each([
    { button: 'Enviar', endpoint: 'enviar', next: 'ENVIADA' },
    { button: 'Cancelar', endpoint: 'cancelar', next: 'CANCELADA' },
  ])('confirma $button y muestra el estado devuelto al recargar', async ({ button, endpoint, next }) => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const { user, row } = await openOrders()
    await user.click(row.getByRole('button', { name: button }))
    expect(api).toHaveBeenCalledWith(`/api/ordenes-compra/42/${endpoint}`, { method: 'POST' })
    await waitFor(() => expect(row.getByText(next)).toBeInTheDocument())
    expect(row.queryByRole('button', { name: 'Enviar' })).not.toBeInTheDocument()
  })

  it('no envía la orden si el usuario rechaza la confirmación', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const { user, row } = await openOrders()
    await user.click(row.getByRole('button', { name: 'Enviar' }))
    expect(api).not.toHaveBeenCalledWith('/api/ordenes-compra/42/enviar', { method: 'POST' })
    expect(row.getByText('PENDIENTE')).toBeInTheDocument()
  })

  it('conserva el estado y muestra el error si el envío falla', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const { user, row } = await openOrders()
    vi.mocked(api).mockRejectedValueOnce(new Error('No se pudo enviar la orden'))
    await user.click(row.getByRole('button', { name: 'Enviar' }))
    expect(await screen.findByRole('alertdialog')).toHaveTextContent('No se pudo enviar la orden')
    expect(row.getByText('PENDIENTE')).toBeInTheDocument()
  })
})
