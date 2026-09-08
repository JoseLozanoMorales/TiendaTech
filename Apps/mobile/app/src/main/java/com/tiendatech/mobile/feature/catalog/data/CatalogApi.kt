package com.tiendatech.mobile.feature.catalog.data

import com.tiendatech.mobile.core.network.ApiEnvelope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CatalogApi {
    @GET("api/productos")
    suspend fun products(@Query("page") page: Int = 0, @Query("size") size: Int = 5): Response<ApiEnvelope<List<ProductDto>>>

    @GET("api/categorias")
    suspend fun categories(): Response<ApiEnvelope<List<CategoryDto>>>

    @GET("api/productos/por-categoria")
    suspend fun productsByCategory(
        @Query("categoriaId") categoryId: Long,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 5
    ): Response<ApiEnvelope<List<ProductDto>>>

    @GET("api/productos/{id}")
    suspend fun product(@Path("id") id: Long): Response<ApiEnvelope<ProductDto>>

    @GET("api/galeria_v2/producto/{id}")
    suspend fun gallery(@Path("id") id: Long, @Query("scope") scope: String = "galeria"): Response<ApiEnvelope<List<GalleryDto>>>
}

@Serializable
data class ProductDto(
    @SerialName("producto_id") val productIdSnake: Long? = null,
    val productoId: Long? = null,
    @SerialName("id_producto") val legacyProductId: Long? = null,
    val id: Long? = null,
    val nombre: String? = null,
    val producto: String? = null,
    val descripcion: String? = null,
    val preciounitario: Double? = null,
    val precioUnitario: Double? = null,
    val precio: Double? = null,
    val costo: Double? = null,
    val stock: Int? = null,
    val habilitado: Boolean? = true,
    @SerialName("categoria_id") val categoryIdSnake: Long? = null,
    val categoriaId: Long? = null,
    val categoria: String? = null,
    @SerialName("categoria_nombre") val categoryNameSnake: String? = null,
    val nombre_categoria: String? = null,
    @SerialName("galeria_id") val galleryIdSnake: Long? = null,
    val galeriaId: Long? = null,
    val imagenId: Long? = null,
    @SerialName("imagen_id") val imageIdSnake: Long? = null,
    val portadaId: Long? = null,
    @SerialName("portada_id") val coverIdSnake: Long? = null
)

@Serializable
data class CategoryDto(
    val id: Long? = null,
    @SerialName("id_categoria") val categoryId: Long? = null,
    val nombre: String = "",
    val habilitado: Boolean? = true
)

@Serializable
data class GalleryDto(
    val id: Long? = null,
    val galeriaId: Long? = null,
    @SerialName("galeria_id") val galleryIdSnake: Long? = null,
    val habilitado: Boolean? = true
)
