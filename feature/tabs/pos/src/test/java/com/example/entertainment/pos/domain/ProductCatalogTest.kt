package com.example.entertainment.pos.domain

import com.example.entertainment.pos.testdata.TestProducts
import com.example.entertainment.pos.testdata.UNKNOWN_BARCODE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductCatalogTest {

    private val catalog = ProductCatalog()

    @Test
    fun `given the catalog then it matches the README table`() {
        assertEquals(TestProducts.ALL, catalog.products)
    }

    @Test
    fun `given the catalog then barcodes are unique 12-digit UPCs and prices are positive`() {
        assertEquals(catalog.products.size, catalog.products.map { it.barcode }.toSet().size)
        catalog.products.forEach {
            assertTrue(it.barcode, it.barcode.matches(Regex("\\d{12}")))
            assertTrue(it.name, it.priceCents > 0)
        }
    }

    @Test
    fun `given a known barcode when looked up then the product comes back`() {
        catalog.products.forEach { assertEquals(it, catalog.lookup(it.barcode)) }
    }

    @Test
    fun `given an unknown or malformed barcode when looked up then it is null`() {
        assertNull(catalog.lookup(UNKNOWN_BARCODE))
        assertNull(catalog.lookup(""))
        assertNull(catalog.lookup(" 012345678905"))
        assertNull(catalog.lookup("12345678905"))
    }
}
