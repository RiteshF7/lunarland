package lunar.land.ui.core.model.common

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

fun Double.asPercent(maxFractions: Int = 2) = limitDecimals(maxFractions = maxFractions) + "%"

fun Double.limitDecimals(
    maxFractions: Int = 2,
    minFractions: Int = 2
): String {
    val fractionsPlaceholder = "#".repeat(n = maxFractions)
    val decimalFormat = DecimalFormat("#.$fractionsPlaceholder", DecimalFormatSymbols(Locale.US)).apply {
        minimumFractionDigits = minFractions
        maximumFractionDigits = maxFractions
    }
    return decimalFormat.format(this) ?: this.toString()
}

