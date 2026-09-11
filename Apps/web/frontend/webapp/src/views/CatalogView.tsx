import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../services/api'
import { field, imageFallback, productId, productImage, productName, productPrice, Row, rowId, money } from './shared'

const PAGE_SIZE = 12

export default function CatalogView() {
  const [products, setProducts] = useState<Row[]>([])
  const [categories, setCategories] = useState<Row[]>([])
  const [selected, setSelected] = useState<number | null>(null)
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(true)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState('')
  const loadMoreRef = useRef<HTMLDivElement | null>(null)
  const requestId = useRef(0)

  const loadProducts = useCallback(async (category: number | null, requestedPage: number, append: boolean) => {
    const currentRequest = ++requestId.current
    if (append) {
      setLoadingMore(true)
    } else {
      setLoading(true)
    }
    setError('')
    try {
      const categoryQuery = category === null ? '' : `categoriaId=${category}&`
      const endpoint = category === null ? '/api/productos' : '/api/productos/por-categoria'
      const rows = await api<Row[]>(`${endpoint}?${categoryQuery}page=${requestedPage}&size=${PAGE_SIZE}`)
      if (currentRequest !== requestId.current) return
      setProducts((current) => {
        if (!append) return rows
        const existing = new Set(current.map(productId))
        return [...current, ...rows.filter((row) => !existing.has(productId(row)))]
      })
      setPage(requestedPage)
      setHasMore(rows.length === PAGE_SIZE)
    } catch (cause) {
      if (currentRequest === requestId.current) {
        setError(cause instanceof Error ? cause.message : 'No se pudo cargar el catálogo.')
      }
    } finally {
      if (currentRequest === requestId.current) {
        setLoading(false)
        setLoadingMore(false)
      }
    }
  }, [])

  useEffect(() => {
    void api<Row[]>('/api/categorias').then(setCategories).catch(() => setCategories([]))
    void loadProducts(null, 0, false)
  }, [loadProducts])

  const loadMore = useCallback(() => {
    if (!loading && !loadingMore && hasMore) void loadProducts(selected, page + 1, true)
  }, [hasMore, loadProducts, loading, loadingMore, page, selected])

  useEffect(() => {
    const target = loadMoreRef.current
    if (!target || !hasMore || loading || loadingMore) return
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) loadMore()
    }, { rootMargin: '300px' })
    observer.observe(target)
    return () => observer.disconnect()
  }, [hasMore, loadMore, loading, loadingMore, products.length])

  const visible = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase()
    return products.filter((product) => productName(product).toLowerCase().includes(normalizedQuery))
  }, [products, query])

  const choose = (value: number | null) => {
    setSelected(value)
    setPage(0)
    setHasMore(true)
    void loadProducts(value, 0, false)
  }

  return <>
    <section className="hero"><div><p className="eyebrow">Tecnología a tu medida</p><h1>Todo para construir<br /><em>algo increíble.</em></h1><p>Explora componentes, compara opciones y arma el equipo ideal.</p></div><div className="hero-orbit" aria-hidden="true"><span>CPU</span><span>GPU</span><span>RAM</span><b>TT</b></div></section>
    <section className="catalog">
      <div className="section-heading"><div><p className="eyebrow">Catálogo</p><h2>Encuentra tu componente</h2></div><input value={query} onChange={(e) => setQuery(e.target.value)} className="search" type="search" placeholder="Buscar producto…" aria-label="Buscar producto" /></div>
      <div className="chips"><button className={selected === null ? 'active' : ''} onClick={() => choose(null)}>Todos</button>{categories.map((category) => { const id = rowId(category, 'id', 'id_categoria'); return <button key={id} className={selected === id ? 'active' : ''} onClick={() => choose(id)}>{String(field(category, 'nombre') || '')}</button> })}</div>
      {loading ? <p className="status">Cargando productos…</p> : <>
        {error && !products.length ? <p className="alert">{error}</p> : visible.length ? <div className="product-grid">{visible.map((product) => <article key={productId(product)} className="product-card"><img src={productImage(product)} alt={productName(product)} onError={imageFallback} /><div><p className="product-category">{String(field(product, 'categoria', 'categoria_nombre', 'nombre_categoria') || 'Componente')}</p><h3>{productName(product)}</h3><p className="product-description">{String(field(product, 'descripcion', 'detalle') || 'Disponible en TiendaTech')}</p><div className="product-footer"><strong>{money(productPrice(product))}</strong><Link to={`/producto/${productId(product)}`}>Ver detalle</Link></div></div></article>)}</div> : <p className="status">No encontramos productos en las páginas cargadas.</p>}
        {error && products.length > 0 && <p className="alert">{error}</p>}
        {hasMore && <div ref={loadMoreRef} className="status"><button type="button" onClick={loadMore} disabled={loadingMore}>{loadingMore ? 'Cargando más productos…' : 'Cargar más productos'}</button></div>}
        {!hasMore && products.length > 0 && <p className="status">Has llegado al final del catálogo.</p>}
      </>}
    </section>
  </>
}
