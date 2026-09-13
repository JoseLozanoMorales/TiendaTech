import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CatalogView from '../src/views/CatalogView'
import { api } from '../src/services/api'

vi.mock('../src/services/api', () => ({ api: vi.fn() }))

class IntersectionObserverStub {
  observe = vi.fn()
  disconnect = vi.fn()
  unobserve = vi.fn()
}

const categorias = [
  { id: 1, nombre: 'Procesadores' },
  { id: 2, nombre: 'Memorias' },
]

const producto = (id: number, nombre: string, precio = 10) => ({
  producto_id: id, nombre, preciounitario: precio,
})

function mockApi(overrides: Record<string, unknown> = {}) {
  vi.mocked(api).mockImplementation(async (path: string) => {
    if (path === '/api/categorias') return categorias
    for (const key of Object.keys(overrides)) {
      if (path.startsWith(key)) return overrides[key]
    }
    return [producto(1, 'Producto 1'), producto(2, 'Producto 2')]
  })
}

const openCatalog = () => render(<MemoryRouter><CatalogView /></MemoryRouter>)

beforeEach(() => {
  vi.stubGlobal('IntersectionObserver', IntersectionObserverStub)
  mockApi()
})

describe('Catálogo: listado y búsqueda', () => {
  it('muestra los productos cargados y filtra por texto', async () => {
    const user = userEvent.setup()
    openCatalog()

    expect(await screen.findByText('Producto 1')).toBeInTheDocument()
    expect(screen.getByText('Producto 2')).toBeInTheDocument()

    await user.type(screen.getByPlaceholderText('Buscar producto…'), 'Producto 2')

    expect(screen.queryByText('Producto 1')).not.toBeInTheDocument()
    expect(screen.getByText('Producto 2')).toBeInTheDocument()
  })

  it('cambia de categoría al hacer clic en un chip', async () => {
    const user = userEvent.setup()
    mockApi({ '/api/productos/por-categoria': [producto(9, 'Memoria RAM 16GB')] })
    openCatalog()
    await screen.findByText('Producto 1')

    await user.click(screen.getByRole('button', { name: 'Memorias' }))

    expect(await screen.findByText('Memoria RAM 16GB')).toBeInTheDocument()
    await waitFor(() => expect(api).toHaveBeenCalledWith(
      expect.stringContaining('/api/productos/por-categoria?categoriaId=2&page=0&size=12'),
    ))
  })

  it('muestra el estado vacío cuando no hay productos', async () => {
    mockApi({ '/api/productos?': [] })
    openCatalog()
    expect(await screen.findByText('No encontramos productos en las páginas cargadas.')).toBeInTheDocument()
  })

  it('muestra un mensaje de error cuando falla la carga inicial', async () => {
    vi.mocked(api).mockImplementation(async (path: string) => {
      if (path === '/api/categorias') return categorias
      throw new Error('No se pudo conectar con el servidor.')
    })
    openCatalog()
    expect(await screen.findByText('No se pudo conectar con el servidor.')).toBeInTheDocument()
  })

  it('carga más productos al hacer clic en "Cargar más productos"', async () => {
    const firstPage = Array.from({ length: 12 }, (_, i) => producto(i + 1, `Producto ${i + 1}`))
    const secondPage = [producto(13, 'Producto 13')]
    let call = 0
    vi.mocked(api).mockImplementation(async (path: string) => {
      if (path === '/api/categorias') return categorias
      call += 1
      return call === 1 ? firstPage : secondPage
    })
    const user = userEvent.setup()
    openCatalog()

    await screen.findByText('Producto 1')
    expect(screen.getByText('Producto 12')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Cargar más productos' }))

    expect(await screen.findByText('Producto 13')).toBeInTheDocument()
  })
})
