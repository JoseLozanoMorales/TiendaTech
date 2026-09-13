import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import BuilderView from '../src/views/BuilderView'
import { api } from '../src/services/api'
import { addToCart } from '../src/services/cart'
import { getUser } from '../src/services/session'

vi.mock('../src/services/api', () => ({ api: vi.fn() }))
vi.mock('../src/services/cart', () => ({ addToCart: vi.fn() }))
vi.mock('../src/services/session', () => ({ getUser: vi.fn() }))

const cpu = { id: 1, nombre: 'Ryzen 5', precio: 150 }

const openBuilder = () => render(<MemoryRouter><BuilderView /></MemoryRouter>)

describe('Armador de PC', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.mocked(api).mockImplementation(async (path: string) => {
      if (path.includes('/por-categoria')) return [cpu]
      return {}
    })
  })

  it('carga los componentes de la categoría inicial (Procesador)', async () => {
    openBuilder()
    expect(await screen.findByText('Ryzen 5')).toBeInTheDocument()
    expect(api).toHaveBeenCalledWith(expect.stringContaining('categoriaId=2'))
  })

  it('selecciona un componente y actualiza el resumen y el total', async () => {
    const user = userEvent.setup()
    openBuilder()
    await screen.findByText('Ryzen 5')

    await user.click(screen.getByRole('button', { name: 'Elegir' }))

    expect(await screen.findByText('Ryzen 5 añadido a la configuración.')).toBeInTheDocument()
    expect(screen.getByText('Total estimado').closest('div')).toHaveTextContent(/150[,.]00/)
    expect(screen.getByRole('button', { name: 'Seleccionado' })).toBeInTheDocument()
  })

  it('redirige a login al intentar añadir la configuración sin sesión', async () => {
    vi.mocked(getUser).mockReturnValue(null)
    const user = userEvent.setup()
    openBuilder()
    await screen.findByText('Ryzen 5')
    await user.click(screen.getByRole('button', { name: 'Elegir' }))

    await user.click(screen.getByRole('button', { name: 'Añadir al carrito' }))

    expect(addToCart).not.toHaveBeenCalled()
  })

  it('añade la configuración al carrito cuando hay sesión', async () => {
    vi.mocked(getUser).mockReturnValue({ usuarioId: 7 })
    vi.mocked(addToCart).mockResolvedValue(undefined)
    const user = userEvent.setup()
    openBuilder()
    await screen.findByText('Ryzen 5')
    await user.click(screen.getByRole('button', { name: 'Elegir' }))

    await user.click(screen.getByRole('button', { name: 'Añadir al carrito' }))

    await waitFor(() => expect(addToCart).toHaveBeenCalledWith(1, 1))
    expect(await screen.findByText('Configuración añadida al carrito.')).toBeInTheDocument()
  })

  it('muestra el estado vacío cuando la categoría no tiene componentes', async () => {
    vi.mocked(api).mockResolvedValue([])
    openBuilder()
    expect(await screen.findByText('No hay procesador en CRDB')).toBeInTheDocument()
  })
})
