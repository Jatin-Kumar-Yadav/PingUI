package com.pingpaisa.app

import com.pingpaisa.app.data.Transaction
import com.pingpaisa.app.data.isIncome
import com.pingpaisa.app.healthscore.Money

/** "−₹320" for money going out, "+₹5,000" for money coming in (the signs the design uses). */
internal fun amountText(t: Transaction): String =
    if (t.isIncome) "+" + Money.inr(t.amount) else Money.inr(-t.amount)

/** How a transaction got into the app, for the small note under its name. */
internal fun sourceLabel(t: Transaction): String = if (t.source == "manual") "Manual" else "UPI"
