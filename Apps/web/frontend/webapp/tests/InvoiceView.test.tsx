import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import InvoiceView from '../src/views/InvoiceView'
import { api } from '../src/services/api'

vi.mock('../src/services/api', () => ({ api: vi.fn() }))

const factura = { facturaId: 42, cliente: 'Ada Cliente', fecha: '2026-01-01', estado: 'Emitida', total: 71.4 }

const openInvoice = (path: string) => render(
  <MemoryRouter initialEntries={[path]}>
    <Routes><Route path="/factura/:id?" element={<InvoiceView />} /></Routes>
  </MemoryRouter>,
)

describe('Factura', () => {
  beforeEach(() => {
    vi.mocked(api).mockReset()
  })

  it('muestra los datos de la factura por id de ruta', async () => {
    vi.mocked(api).mockResolvedValue(factura)
    openInvoice('/factura/42')

    expect(await screen.findByRole('heading', { name: 'Factura #42' })).toBeInTheDocument()
    expect(screen.getByText('Ada Cliente')).toBeInTheDocument()
    expect(api).toHaveBeenCalledWith('/api/facturas/42')
  })

  it('muestra un error cuando no se indica una factura', async () => {
    openInvoice('/factura')
    expect(await screen.findByText('No se indicó una factura.')).toBeInTheDocument()
    expect(api).not.toHaveBeenCalled()
  })

  it('muestra un error cuando falla la carga de la factura', async () => {
    vi.mocked(api).mockRejectedValue(new Error('No se pudo cargar la factura.'))
    openInvoice('/factura/99')
    expect(await screen.findByText('No se pudo cargar la factura.')).toBeInTheDocument()
  })
})
