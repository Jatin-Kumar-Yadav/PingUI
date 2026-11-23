package com.pingpaisa.app.data

import com.pingpaisa.app.finance.EmiOption
import com.pingpaisa.app.finance.EmiSource
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID

/** Amounts are stored as strings so no digit is lost to floating point. */
fun EmiOption.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("lender", lender)
    put("monthlyEmi", monthlyEmi?.toPlainString()); put("tenureMonths", tenureMonths)
    put("annualRatePct", annualRatePct?.toPlainString())
    put("downPayment", downPayment.toPlainString()); put("processingFee", processingFee.toPlainString())
    put("otherCharges", otherCharges.toPlainString()); put("totalPayable", totalPayable?.toPlainString())
    put("cashback", cashback.toPlainString()); put("cashDiscountLost", cashDiscountLost.toPlainString())
    put("noCostAdvertised", noCostAdvertised); put("source", source.name); put("confirmed", confirmed)
    put("quotedForPrice", quotedForPrice?.toPlainString()); put("fromLowest", fromLowest); put("evidence", evidence)
}

private fun JSONObject.dec(key: String): BigDecimal? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }?.toBigDecimalOrNull()

fun emiOptionFromJson(o: JSONObject): EmiOption = EmiOption(
    id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
    lender = o.optString("lender"),
    monthlyEmi = o.dec("monthlyEmi"),
    tenureMonths = if (o.isNull("tenureMonths")) null else o.optInt("tenureMonths").takeIf { it > 0 },
    annualRatePct = o.dec("annualRatePct"),
    downPayment = o.dec("downPayment") ?: BigDecimal.ZERO,
    processingFee = o.dec("processingFee") ?: BigDecimal.ZERO,
    otherCharges = o.dec("otherCharges") ?: BigDecimal.ZERO,
    totalPayable = o.dec("totalPayable"),
    cashback = o.dec("cashback") ?: BigDecimal.ZERO,
    cashDiscountLost = o.dec("cashDiscountLost") ?: BigDecimal.ZERO,
    noCostAdvertised = o.optBoolean("noCostAdvertised"),
    source = EmiSource.values().firstOrNull { it.name == o.optString("source") } ?: EmiSource.MANUAL,
    confirmed = o.optBoolean("confirmed", true),
    quotedForPrice = o.dec("quotedForPrice"),
    fromLowest = o.optBoolean("fromLowest"),
    evidence = o.optString("evidence")
)

fun List<EmiOption>.toJsonArray(): JSONArray = JSONArray().also { a -> forEach { a.put(it.toJson()) } }

fun emiOptionsFromJson(a: JSONArray?): List<EmiOption> =
    if (a == null) emptyList() else (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { runCatching { emiOptionFromJson(it) }.getOrNull() } }
