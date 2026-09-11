import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CartView from '../src/views/CartView'
import { api } from '../src/services/api'
import { cartLines, currentCart, removeCartLine, updateCartLine } from '../src/services/cart'
import { getUser } from '../src/services/session'

vi.mock('../src/services/api', () => ({ api: vi.fn() }))
vi.mock('../src/services/session', () => ({ getUser: vi.fn() }))
vi.mock('../src/services/cart', () => ({
  currentCart: vi.fn(), cartLines: vi.fn(),
  updateCartLine: vi.fn(), removeCartLine: vi.fn(),
}))

const line = { carritoId: 12, productoId: 5, cantidad: 2, precioUnitario: 10.5 }
const summaryValue = (label: string) =>
  within(screen.getByRole('complementary')).getByText(label).parentElement!
const openCart = () => render(<MemoryRouter><CartView /></MemoryRouter>)

beforeEach(() => {
  vi.mocked(getUser).mockReturnValue({ usuarioId: 7 })
  vi.mocked(currentCart).mockResolvedValue({ carritoId: 12 })
  vi.mocked(cartLines).mockResolvedValue([{ ...line }])
  vi.mocked(api).mockImplementation(async (path) =>
    path.includes('?') ? [] : { nombre: `Producto ${path.split('/').pop()}` })
  vi.mocked(updateCartLine).mockResolvedValue(undefined)
  vi.mocked(removeCartLine).mockResolvedValue(undefined)
})

describe('Carrito: cantidades y totales', () => {
  it('suma precios por cantidades de varios productos', async () => {
    vi.mocked(cartLines).mockResolvedValue([
      line, { ...line, productoId: 6, cantidad: 3, precioUnitario: 4.25 },
    ])
    openCart()
    await screen.findByRole('heading', { name: 'Producto 5' })
    expect(summaryValue('Productos')).toHaveTextContent('5')
    expect(summaryValue('Subtotal')).toHaveTextContent(/33[,.]75/)
    expect(summaryValue('Total')).toHaveTextContent(/33[,.]75/)
  })

  it('aumenta la cantidad y recalcula el total después de guardar', async () => {
    const user = userEvent.setup()
    openCart()
    await screen.findByRole('spinbutton')
    await user.click(screen.getByRole('button', { name: '+' }))
    await waitFor(() => expect(screen.getByRole('spinbutton')).toHaveValue(3))
    expect(updateCartLine).toHaveBeenCalledExactlyOnceWith(12, 5, 3)
    expect(summaryValue('Total')).toHaveTextContent(/31[,.]50/)
  })

  it.each([
    { quantity: 1, button: '−' },
    { quantity: 99, button: '+' },
  ])('respeta el límite de $quantity unidades', async ({ quantity, button }) => {
    vi.mocked(cartLines).mockResolvedValue([{ ...line, cantidad: quantity }])
    const user = userEvent.setup()
    openCart()
    await screen.findByRole('spinbutton')
    await user.click(screen.getByRole('button', { name: button }))
    await waitFor(() => expect(updateCartLine).toHaveBeenCalledExactlyOnceWith(12, 5, quantity))
    expect(screen.getByRole('spinbutton')).toHaveValue(quantity)
  })

  it('conserva cantidad y total si falla la actualización', async () => {
    vi.mocked(updateCartLine).mockRejectedValue(new Error('Stock insuficiente'))
    const user = userEvent.setup()
    openCart()
    await screen.findByRole('spinbutton')
    await user.click(screen.getByRole('button', { name: '+' }))
    expect(await screen.findByText('Stock insuficiente')).toBeInTheDocument()
    expect(screen.getByRole('spinbutton')).toHaveValue(2)
    expect(summaryValue('Total')).toHaveTextContent(/21[,.]00/)
  })

  it('quita el último producto y muestra el carrito vacío', async () => {
    const user = userEvent.setup()
    openCart()
    await user.click(await screen.findByRole('button', { name: 'Quitar' }))

    expect(await screen.findByText('Tu carrito está vacío')).toBeInTheDocument()
    expect(removeCartLine).toHaveBeenCalledExactlyOnceWith(12, 5)
    expect(screen.queryByRole('spinbutton')).not.toBeInTheDocument()
  })

  it('conserva el producto si falla su eliminación', async () => {
    vi.mocked(removeCartLine).mockRejectedValue(new Error('No se pudo quitar'))
    const user = userEvent.setup()
    openCart()
    await user.click(await screen.findByRole('button', { name: 'Quitar' }))

    expect(await screen.findByText('No se pudo quitar')).toBeInTheDocument()
    expect(screen.getByRole('spinbutton')).toHaveValue(2)
    expect(summaryValue('Total')).toHaveTextContent(/21[,.]00/)
  })

  it('muestra el estado vacío cuando no hay productos', async () => {
    vi.mocked(cartLines).mockResolvedValue([])
    openCart()

    expect(await screen.findByText('Tu carrito está vacío')).toBeInTheDocument()
    expect(
        screen.queryByRole('link', { name: 'Continuar al pago' }),
    ).not.toBeInTheDocument()
  })
})
