import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import CheckoutView from '../src/views/CheckoutView'
import { api } from '../src/services/api'
import { cartLines, currentCart } from '../src/services/cart'
import { getUser } from '../src/services/session'

vi.mock('../src/services/api', () => ({ api: vi.fn() }))
vi.mock('../src/services/cart', () => ({ currentCart: vi.fn(), cartLines: vi.fn() }))
vi.mock('../src/services/session', () => ({ getUser: vi.fn() }))

describe('Pago: cálculo de totales', () => {
  it.each([
    { name: 'carrito vacío', lines: [], units: '0', amount: /0[,.]00/ },
    { name: 'varios productos con decimales', lines: [
      { carritoId: 12, productoId: 5, cantidad: 2, precioUnitario: 10.5 },
      { carritoId: 12, productoId: 6, cantidad: 3, precioUnitario: 4.25 },
    ], units: '5', amount: /33[,.]75/ },
  ])('calcula unidades, subtotal y total: $name', async ({ lines, units, amount }) => {
    vi.mocked(getUser).mockReturnValue({ usuarioId: 7 })
    vi.mocked(currentCart).mockResolvedValue({ carritoId: 12 })
    vi.mocked(cartLines).mockResolvedValue(lines)
    vi.mocked(api).mockImplementation(async (path) =>
        path.includes('metodopago') ? { content: [] } : [])
    render(<MemoryRouter><CheckoutView /></MemoryRouter>)
    const summary = within(await screen.findByRole('complementary'))
    expect(summary.getByText('Unidades').parentElement).toHaveTextContent(units)
    expect(summary.getByText('Subtotal').parentElement).toHaveTextContent(amount)
    expect(summary.getByText('Total').parentElement).toHaveTextContent(amount)
  })
})
