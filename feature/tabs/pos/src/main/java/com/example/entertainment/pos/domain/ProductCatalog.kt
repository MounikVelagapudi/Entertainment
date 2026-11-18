package com.example.entertainment.pos.domain

import com.example.entertainment.pos.domain.model.Product
import javax.inject.Inject
import javax.inject.Singleton

/** Hardcoded stand-in for a synced product catalog. */
@Singleton
class ProductCatalog @Inject constructor() {

    val products: List<Product> = listOf(
        Product("012345678905", "Popcorn (L)", 899),
        Product("036000291452", "Nachos", 749),
        Product("042100005264", "Soda (M)", 499),
        Product("070847811169", "Candy Bar", 325),
        Product("490000000016", "Hot Dog", 599),
        Product("885909950805", "Movie Ticket", 1499),
    )

    private val byBarcode = products.associateBy { it.barcode }

    fun lookup(barcode: String): Product? = byBarcode[barcode]
}
