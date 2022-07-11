package com.woocommerce.android.ui.orders.creation

import com.woocommerce.android.R
import com.woocommerce.android.model.Order
import com.woocommerce.android.ui.orders.creation.OrderCreationViewModel.MultipleLinesContext
import com.woocommerce.android.viewmodel.ResourceProvider
import java.util.Locale
import javax.inject.Inject

class DetermineMultipleLinesContext @Inject constructor(private val resourceProvider: ResourceProvider) {
    operator fun invoke(order: Order) =
        when {
            order.hasMultipleShippingLines && order.hasMultipleFeeLines -> MultipleLinesContext.Warning(
                header = resourceProvider.getString(
                    R.string.lines_incomplete,
                    String.format(
                        Locale.getDefault(),
                        "%s & %s",
                        resourceProvider.getString(R.string.order_creation_payment_fee),
                        resourceProvider.getString(R.string.orderdetail_shipping_details),
                    )
                ),
                explanation = resourceProvider.getString(
                    R.string.lines_incomplete_explanation,
                    resourceProvider.getString(R.string.lines_all_details)
                ),
            )
            order.hasMultipleFeeLines -> MultipleLinesContext.Warning(
                header = resourceProvider.getString(
                    R.string.lines_incomplete,
                    resourceProvider.getString(R.string.order_creation_payment_fee)
                ),
                explanation = resourceProvider.getString(
                    R.string.lines_incomplete_explanation,
                    resourceProvider.getString(R.string.order_creation_payment_fee).lowercase()
                )
            )
            order.hasMultipleShippingLines -> MultipleLinesContext.Warning(
                header = resourceProvider.getString(
                    R.string.lines_incomplete,
                    resourceProvider.getString(R.string.orderdetail_shipping_details)
                ),
                explanation = resourceProvider.getString(
                    R.string.lines_incomplete_explanation,
                    resourceProvider.getString(R.string.orderdetail_shipping_details).lowercase(),
                ),
            )
            else -> MultipleLinesContext.None
        }
}
