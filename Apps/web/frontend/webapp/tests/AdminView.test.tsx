import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminView from '../src/views/AdminView'
import { api } from '../src/services/api'
import { getUser } from '../src/services/session'

vi.mock('../src/services/api', () => ({ api: vi.fn() }))
vi.mock('../src/services/session', () => ({ getUser: vi.fn() }))

const admin = { usuarioId: 1, nombre: 'Ada Admin', usuario: 'ada' }
const categorias = [{ id: 1, nombre: 'Almacenamiento' }]
const marcas = [{ id: 1, nombre: 'Kingston' }]
const gamas = [{ id: 1, nombre: 'Media' }]
const ivas = [{ iva_id: 1, porcentaje: 15 }]
const productos = [{ producto_id: 10, nombre: 'SSD 1TB', preciounitario: 60, costo: 40, stock: 5, habilitado: true }]
const admins = [{ usuarioId: 1, usuario: 'ada', nombre: 'Ada Admin', correo: 'ada@tienda.com' }]
const clientes = [{ usuarioId: 2, usuario: 'cli', nombre: 'Cliente Uno', correo: 'cli@tienda.com' }]
const trabajadores = [{ usuarioId: 3, usuario: 'work', nombre: 'Worker Uno', correo: 'w@tienda.com' }]

function mockApiForLoad(overrides: Record<string, unknown> = {}) {
  vi.mocked(api).mockImplementation(async (path: string) => {
    if (Object.prototype.hasOwnProperty.call(overrides, path)) return overrides[path]
    if (path.startsWith('/api/productos?')) return productos
    if (path === '/api/categorias') return categorias
    if (path === '/api/marcas') return marcas
    if (path === '/api/gamas') return gamas
    if (path === '/api/sp/ivas') return ivas
    if (path.includes('rolId=1')) return admins
    if (path.includes('rolId=2')) return clientes
    if (path.includes('rolId=3')) return trabajadores
    return []
  })
}

const openAdmin = () => render(<MemoryRouter><AdminView /></MemoryRouter>)

beforeEach(() => {
  vi.mocked(getUser).mockReturnValue(admin)
  mockApiForLoad()
  vi.spyOn(window, 'confirm').mockReturnValue(true)
})

describe('AdminView: resumen', () => {
  it('muestra las métricas cargadas desde los servicios', async () => {
    openAdmin()
    await screen.findByText('Productos')
    expect(screen.getByText('Productos').closest('article')).toHaveTextContent('1')
    expect(screen.getByText('Administradores').closest('article')).toHaveTextContent('1')
    expect(screen.getByText('Trabajadores').closest('article')).toHaveTextContent('1')
  })
})

describe('AdminView: usuarios', () => {
  it('crea un nuevo administrador y refresca la lista', async () => {
    const user = userEvent.setup()
    openAdmin()
    await screen.findByText('Productos')
    await user.click(screen.getByRole('button', { name: '♙ Usuarios' }))
    await user.click(screen.getByRole('button', { name: '+ Crear usuario' }))

    await user.type(screen.getByLabelText('Nombre completo'), 'Nuevo Admin')
    await user.type(screen.getByLabelText('Cedula'), '1234567890')
    await user.type(screen.getByLabelText('Correo'), 'nuevo@tienda.com')
    await user.type(screen.getByLabelText('Telefono'), '0999999999')
    await user.type(screen.getByLabelText('Usuario'), 'nuevoadmin')
    await user.type(screen.getByLabelText('Contraseña'), 'clave1234')
    await user.click(screen.getByRole('button', { name: 'Guardar usuario' }))

    await waitFor(() => expect(api).toHaveBeenCalledWith(
      '/api/usuarios/crear-usuarioAdmin',
      expect.objectContaining({ method: 'POST' }),
    ))
    expect(await screen.findByText('Usuario creado correctamente.')).toBeInTheDocument()
  })

  it('deshabilita un usuario tras confirmar', async () => {
    const user = userEvent.setup()
    openAdmin()
    await screen.findByText('Productos')
    await user.click(screen.getByRole('button', { name: '♙ Usuarios' }))
    await screen.findByText('ada', { selector: 'strong' })

    await user.click(screen.getByRole('button', { name: 'Deshabilitar' }))

    await waitFor(() => expect(api).toHaveBeenCalledWith(
      expect.stringContaining('/api/usuarios/admin/1'),
      expect.objectContaining({ method: 'DELETE' }),
    ))
    expect(await screen.findByText('Usuario deshabilitado.')).toBeInTheDocument()
  })

  it('filtra usuarios por texto de búsqueda', async () => {
    const user = userEvent.setup()
    openAdmin()
    await screen.findByText('Productos')
    await user.click(screen.getByRole('button', { name: '♙ Usuarios' }))
    await screen.findByText('ada', { selector: 'strong' })

    await user.type(screen.getByPlaceholderText('Buscar usuario…'), 'zzz-sin-coincidencias')

    expect(await screen.findByText('No hay usuarios de este rol. Puedes crear el primero.')).toBeInTheDocument()
    expect(screen.queryByText('ada', { selector: 'strong' })).not.toBeInTheDocument()
  })
})

describe('AdminView: productos', () => {
  it('muestra el catálogo y permite deshabilitar un producto', async () => {
    const user = userEvent.setup()
    openAdmin()
    await screen.findByText('Productos')
    await user.click(screen.getByRole('button', { name: '◇ Productos' }))

    expect(await screen.findByText('SSD 1TB')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Deshabilitar' }))

    await waitFor(() => expect(api).toHaveBeenCalledWith(
      expect.stringContaining('/api/sp/productos/10'),
      expect.objectContaining({ method: 'DELETE' }),
    ))
    expect(await screen.findByText('Producto deshabilitado.')).toBeInTheDocument()
  })
})

describe('AdminView: sistema distribuido', () => {
  it('renderiza el estado de coordinación y los servicios observados', async () => {
    mockApiForLoad({
      '/api/reservas/status': { tcp: true, tcpPort: 9001, grpc: true, grpcPort: 9002, lamport: 5, framing: 'length-prefixed' },
      '/api/admin/system': {
        checkedAt: '2026-01-01T00:00:00.000Z',
        coordination: 'saga',
        coordinationSource: 'config-servidor',
        services: [{ service: 'gateway', status: 'UP', latencyMs: 12, checkedAt: '2026-01-01T00:00:00.000Z' }],
      },
      '/api/ordenes/transacciones': [],
    })
    const user = userEvent.setup()
    openAdmin()
    await screen.findByText('Productos')
    await user.click(screen.getByRole('button', { name: '◎ Sistema CRDB' }))

    expect(await screen.findByText('SAGA')).toBeInTheDocument()
    expect(screen.getByText('config-servidor')).toBeInTheDocument()
    expect(screen.getByText('Puerto 9001 · length-prefixed')).toBeInTheDocument()
    expect(screen.getByText('Gateway')).toBeInTheDocument()
  })
})
