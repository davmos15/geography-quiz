package com.geoquiz.app.data.service

import com.android.billingclient.api.Purchase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class PurchaseActionTest {

    private fun purchase(
        products: List<String> = listOf(BillingRepository.PRODUCT_ID),
        state: Int = Purchase.PurchaseState.PURCHASED,
        acknowledged: Boolean = false
    ): Purchase = mockk {
        every { this@mockk.products } returns products
        every { purchaseState } returns state
        every { isAcknowledged } returns acknowledged
    }

    @Test
    fun `unacknowledged purchase is acknowledged and granted`() {
        assertEquals(
            PurchaseAction.ACKNOWLEDGE_AND_GRANT,
            purchaseActionFor(purchase(acknowledged = false))
        )
    }

    @Test
    fun `acknowledged purchase is granted without re-acknowledging`() {
        assertEquals(
            PurchaseAction.GRANT,
            purchaseActionFor(purchase(acknowledged = true))
        )
    }

    @Test
    fun `pending purchase is not granted`() {
        assertEquals(
            PurchaseAction.PENDING,
            purchaseActionFor(purchase(state = Purchase.PurchaseState.PENDING))
        )
    }

    @Test
    fun `unspecified state is ignored`() {
        assertEquals(
            PurchaseAction.IGNORE,
            purchaseActionFor(purchase(state = Purchase.PurchaseState.UNSPECIFIED_STATE))
        )
    }

    @Test
    fun `purchase for another product is ignored`() {
        assertEquals(
            PurchaseAction.IGNORE,
            purchaseActionFor(purchase(products = listOf("something_else")))
        )
    }
}
