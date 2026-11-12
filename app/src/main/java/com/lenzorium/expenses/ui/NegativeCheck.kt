package com.lenzorium.expenses.ui

import com.lenzorium.expenses.data.Txn
import com.lenzorium.expenses.domain.LedgerMath
import com.lenzorium.expenses.domain.Money

/**
 * Returns a warning when saving [new] (replacing [old], if editing) would push an account
 * or the total available balance below zero, or null when everything stays positive.
 */
fun negativeWarning(state: LedgerState, old: Txn?, new: Txn): String? {
    val c = state.currency
    val parts = mutableListOf<String>()
    for (accId in setOfNotNull(new.accountId, new.toAccountId)) {
        val acc = state.accountById[accId] ?: continue
        // Cash and mobile wallets can never go below zero: the save itself is refused with an explanation.
        if (acc.kind == com.lenzorium.expenses.data.AccountKind.CASH || acc.kind == com.lenzorium.expenses.data.AccountKind.WALLET) continue
        val before = state.balanceOf(accId)
        val after = before - (old?.let { LedgerMath.accountDelta(it, accId) } ?: 0L) + LedgerMath.accountDelta(new, accId)
        if (after < 0 && after < before) parts += "${acc.name} will be ${Money.format(after, state.currencyOf(accId))}"
    }
    val f = state.foreignIds
    val total = state.available - (old?.let { LedgerMath.cashDelta(it, f) } ?: 0L) + LedgerMath.cashDelta(new, f)
    if (total < 0 && total < state.available) parts += "your total available balance will be ${Money.format(total, c)}"
    return if (parts.isEmpty()) null else "After saving, ${parts.joinToString(" and ")}. Save anyway?"
}
