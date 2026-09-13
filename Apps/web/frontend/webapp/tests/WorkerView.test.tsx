import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import WorkerView from '../src/views/WorkerView'
import { api } from '../src/services/api'

vi.mock('../src/services/api', () => ({ api: vi.fn() }))

const openWorker = () => render(<MemoryRouter><WorkerView /></MemoryRouter>)

describe('Panel de trabajador', () => {
  it('muestra las métricas de productos y movimientos cargados', async () => {
    vi.mocked(api).mockImplementation(async (path: string) => {
      if (path === '/api/movimientos') return [{}, {}, {}]
      if (path.startsWith('/api/productos')) return [{}, {}]
      return []
    })
    openWorker()

    expect(await screen.findByText('Productos visibles').then((el) => el.closest('article'))).toHaveTextContent('2')
    expect(screen.getByText('Movimientos').closest('article')).toHaveTextContent('3')
  })

  it('sigue funcionando cuando los servicios fallan', async () => {
    vi.mocked(api).mockRejectedValue(new Error('sin conexión'))
    openWorker()

    expect(await screen.findByText('Productos visibles').then((el) => el.closest('article'))).toHaveTextContent('0')
    expect(screen.getByText('Movimientos').closest('article')).toHaveTextContent('0')
  })
})
