package com.tiendatech.mobile.feature.catalog.data

import com.tiendatech.mobile.core.database.dao.CategoryCacheDao
import com.tiendatech.mobile.core.database.dao.ProductCacheDao
import com.tiendatech.mobile.feature.catalog.domain.CatalogResult
import com.tiendatech.mobile.feature.catalog.domain.CatalogSnapshot
import com.tiendatech.mobile.feature.catalog.domain.Category
import com.tiendatech.mobile.feature.catalog.domain.Product
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepository @Inject constructor(
    private val api: CatalogApi,
    private val productDao: ProductCacheDao,
    private val categoryDao: CategoryCacheDao
) {
    companion object { const val PAGE_SIZE = 5 }

    fun observeCatalog(): Flow<CatalogSnapshot> = combine(
        productDao.observeEnabledProducts(), categoryDao.observeEnabledCategories()
    ) { products, categories ->
        CatalogSnapshot(products.map(CatalogMapper::domain), categories.map(CatalogMapper::domain))
    }

    suspend fun refresh(categoryId: Long? = null): CatalogResult<Boolean> = request {
        coroutineScope {
            val productsCall = async {
                if (categoryId == null) api.products(page = 0, size = PAGE_SIZE)
                else api.productsByCategory(categoryId, page = 0, size = PAGE_SIZE)
            }
            val categoriesCall = async { api.categories() }
            val productsResponse = productsCall.await()
            val categoriesResponse = categoriesCall.await()
            if (!productsResponse.isSuccessful) return@coroutineScope failure(productsResponse.code())
            if (!categoriesResponse.isSuccessful) return@coroutineScope failure(categoriesResponse.code())
            val products = productsResponse.body()?.data.orEmpty().mapNotNull(CatalogMapper::product)
            val categories = categoriesResponse.body()?.data.orEmpty().mapNotNull(CatalogMapper::category)
            productDao.clear()
            productDao.upsertAll(products)
            categoryDao.clear()
            categoryDao.upsertAll(categories)
            CatalogResult.Success(products.size == PAGE_SIZE)
        }
    }

    suspend fun loadNextPage(page: Int, categoryId: Long? = null): CatalogResult<Boolean> = request {
        val response = if (categoryId == null) api.products(page, PAGE_SIZE)
        else api.productsByCategory(categoryId, page, PAGE_SIZE)
        if (!response.isSuccessful) return@request failure(response.code())
        val body = response.body()?.data.orEmpty()
        val category = categoryId?.let { id ->
            categoryDao.findById(id)?.let(CatalogMapper::domain)
        }
        productDao.upsertAll(body.mapNotNull { CatalogMapper.product(it, category) })
        CatalogResult.Success(body.size == PAGE_SIZE)
    }

    suspend fun product(id: Long): CatalogResult<Product> = request {
        val response = api.product(id)
        if (response.isSuccessful) {
            val entity = response.body()?.data?.let(CatalogMapper::product)
                ?: productDao.findById(id)
                ?: return@request CatalogResult.Failure("Producto no encontrado")
            productDao.upsertAll(listOf(entity))
            val galleryResponse = api.gallery(id)
            val galleryIds = if (galleryResponse.isSuccessful) {
                galleryResponse.body()?.data.orEmpty().filter { it.habilitado != false }
                    .mapNotNull { it.id ?: it.galeriaId ?: it.galleryIdSnake }
            } else emptyList()
            CatalogResult.Success(CatalogMapper.domain(entity, galleryIds))
        } else {
            val cached = productDao.findById(id) ?: return@request failure(response.code())
            CatalogResult.Success(CatalogMapper.domain(cached))
        }
    }

    suspend fun cachedProduct(id: Long): Product? = productDao.findById(id)?.let(CatalogMapper::domain)

    private suspend fun <T> request(block: suspend () -> CatalogResult<T>): CatalogResult<T> = try {
        block()
    } catch (_: SocketTimeoutException) {
        CatalogResult.Failure("El servidor tardó demasiado en responder")
    } catch (_: IOException) {
        CatalogResult.Failure("Sin conexión. Se muestran los datos guardados")
    } catch (_: Exception) {
        CatalogResult.Failure("No fue posible cargar el catálogo")
    }

    private fun failure(status: Int) = CatalogResult.Failure(when (status) {
        404 -> "Producto no encontrado"
        in 500..599 -> "El catálogo no está disponible temporalmente"
        else -> "No fue posible cargar el catálogo"
    })
}
