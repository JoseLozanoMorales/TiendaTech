import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProductDetailView from '../src/views/ProductDetailView'
import { api } from '../src/services/api'
import { addToCart } from '../src/services/cart'
import { getUser } from '../src/services/session'

vi.mock('../src/services/api', () => ({ api: vi.fn() }))
vi.mock('../src/services/cart', () => ({ addToCart: vi.fn() }))
vi.mock('../src/services/session', () => ({ getUser: vi.fn() }))

const producto = { producto_id: 5, nombre: 'SSD 1TB', preciounitario: 60, descripcion: 'Unidad de estado sólido' }

const openDetail = (id = '5') => render(
  <MemoryRouter initialEntries={[`/producto/${id}`]}>
    <Routes><Route path="/producto/:id" element={<ProductDetailView />} /></Routes>
  </MemoryRouter>,
)

describe('Detalle de producto', () => {
  beforeEach(() => {
    vi.mocked(api).mockImplementation(async (path: string) => {
      if (path === '/api/productos/5') return producto
      if (path.includes('/galeria_v2/producto/5')) return []
      return {}
    })
  })

  it('muestra la información del producto', async () => {
    openDetail()
    expect(await screen.findByRole('heading', { name: 'SSD 1TB' })).toBeInTheDocument()
    expect(screen.getByText('Unidad de estado sólido')).toBeInTheDocument()
  })

  it('muestra un error cuando el id no es válido', async () => {
    openDetail('abc')
    expect(await screen.findByText('Producto inválido.')).toBeInTheDocument()
  })

  it('redirige a login si intenta añadir al carrito sin sesión', async () => {
    vi.mocked(getUser).mockReturnValue(null)
    const user = userEvent.setup()
    openDetail()
    await screen.findByRole('heading', { name: 'SSD 1TB' })

    await user.click(screen.getByRole('button', { name: 'Añadir al carrito' }))

    expect(addToCart).not.toHaveBeenCalled()
  })

  it('añade el producto al carrito cuando hay sesión activa', async () => {
    vi.mocked(getUser).mockReturnValue({ usuarioId: 7 })
    vi.mocked(addToCart).mockResolvedValue(undefined)
    const user = userEvent.setup()
    openDetail()
    await screen.findByRole('heading', { name: 'SSD 1TB' })

    await user.click(screen.getByRole('button', { name: 'Añadir al carrito' }))

    await waitFor(() => expect(addToCart).toHaveBeenCalledWith(5, 1))
    expect(await screen.findByText('Producto añadido al carrito.')).toBeInTheDocument()
  })
})
