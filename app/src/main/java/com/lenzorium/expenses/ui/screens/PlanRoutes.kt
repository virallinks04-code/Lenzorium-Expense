package com.lenzorium.expenses.ui.screens

/** Routes for budgets, bills, shortcuts and goals. */
object PlanRoutes {
    const val BUDGETS = "budgets"
    const val BILLS = "bills"
    const val SHORTCUTS = "shortcuts"
    const val GOALS = "goals"
    fun recurring(id: Long = 0) = "recurring?id=$id"
    fun goal(id: Long) = "goal/$id"
    fun template(templateId: Long, type: com.lenzorium.expenses.data.TxnType) = "entry/${type.name}?id=0&template=$templateId"
}
