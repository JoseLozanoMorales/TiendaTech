import { useEffect, useState } from 'react'
import { Link, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { clearSession, getUser, isAdmin, lastSessionActivity, markSessionActivity, SESSION_CLEARED_EVENT } from './services/session'
import AccountView from './views/AccountView'
import AdminView from './views/AdminView'
import BuilderView from './views/BuilderView'
import CartView from './views/CartView'
import CatalogView from './views/CatalogView'
import CheckoutView from './views/CheckoutView'
import InvoiceView from './views/InvoiceView'
import LoginView from './views/LoginView'
import ProductDetailView from './views/ProductDetailView'
import RecoveryView from './views/RecoveryView'
import RegisterView from './views/RegisterView'
import WorkerView from './views/WorkerView'

function AdminRoute({ children }: { children: React.ReactNode }) {
  const location = useLocation()
  return isAdmin(getUser()) ? children : <Navigate to={`/login?next=${encodeURIComponent(location.pathname)}`} replace />
}

export default function App() {
  const navigate = useNavigate()
  const [user, setUser] = useState(getUser())
  const [dark, setDark] = useState(localStorage.getItem('tt-theme') === 'dark')
  const toggleTheme = () => setDark((value) => { localStorage.setItem('tt-theme', value ? 'light' : 'dark'); return !value })
  const logout = () => { clearSession(); setUser(null); navigate('/login') }
  useEffect(() => {
    if (!user) return
    const idleLimitMs = 30 * 60 * 1000
    let timer = 0
    let lastRecorded = 0
    const expire = () => {
      clearSession()
      setUser(null)
      navigate('/login?reason=inactive', { replace: true })
    }
    const schedule = () => {
      window.clearTimeout(timer)
      const remaining = idleLimitMs - (Date.now() - lastSessionActivity())
      if (remaining <= 0) expire()
      else timer = window.setTimeout(expire, remaining)
    }
    const activity = () => {
      const now = Date.now()
      if (now - lastRecorded >= 15_000) {
        lastRecorded = now
        markSessionActivity()
        schedule()
      }
    }
    const sessionCleared = () => { setUser(null); navigate('/login', { replace: true }) }
    const events: (keyof WindowEventMap)[] = ['pointerdown', 'keydown', 'scroll', 'touchstart']
    events.forEach(event => window.addEventListener(event, activity, { passive: true }))
    window.addEventListener(SESSION_CLEARED_EVENT, sessionCleared)
    schedule()
    return () => {
      window.clearTimeout(timer)
      events.forEach(event => window.removeEventListener(event, activity))
      window.removeEventListener(SESSION_CLEARED_EVENT, sessionCleared)
    }
  }, [user, navigate])
  return <div className={`app-shell${dark ? ' dark' : ''}`}>
    <header className="topbar">
      <Link className="brand" to="/"><span>TT</span> TiendaTech</Link>
      <nav aria-label="Navegación principal"><Link to="/">Productos</Link><Link to="/armado">Arma tu PC</Link><Link to="/carrito">Carrito</Link>{isAdmin(user) && <Link to="/admin">Administración</Link>}</nav>
      <div className="actions"><button className="icon-button" type="button" aria-label={dark ? 'Usar tema claro' : 'Usar tema oscuro'} onClick={toggleTheme}>{dark ? '☀' : '☾'}</button>{user ? <><Link className="text-button" to="/cuenta">{user.nombre || user.usuario || 'Mi cuenta'}</Link><button className="text-button" type="button" onClick={logout}>Salir</button></> : <Link className="button small" to="/login">Ingresar</Link>}</div>
    </header>
    <main><Routes>
      <Route path="/" element={<CatalogView />} />
      <Route path="/login" element={<LoginView onSessionChanged={() => setUser(getUser())} />} />
      <Route path="/producto/:id" element={<ProductDetailView />} />
      <Route path="/carrito" element={<CartView />} />
      <Route path="/admin" element={<AdminRoute><AdminView /></AdminRoute>} />
      <Route path="/armado" element={<BuilderView />} />
      <Route path="/registro" element={<RegisterView />} />
      <Route path="/recuperacion" element={<RecoveryView />} />
      <Route path="/cuenta" element={<AccountView />} />
      <Route path="/pago" element={<CheckoutView />} />
      <Route path="/factura/:id?" element={<InvoiceView />} />
      <Route path="/trabajador" element={<WorkerView />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes></main>
    <footer>© 2026 TiendaTech · Arquitectura de microservicios</footer>
  </div>
}
