package com.sahyog.core.ui.catalog

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sahyog.core.model.CategoryIds
import com.sahyog.core.model.ServiceCategory
import com.sahyog.core.ui.R

/*
 * How a service category looks. Known categories get a translated label and an icon;
 * categories the backend adds later fall back to the server name and a generic icon,
 * so the app never needs an update just to show a new category.
 */

@DrawableRes
fun categoryIconRes(categoryId: String): Int = when (categoryId) {
    CategoryIds.ELECTRICIAN -> R.drawable.ic_category_electrical_services
    CategoryIds.PLUMBER -> R.drawable.ic_category_plumbing
    CategoryIds.CARPENTER -> R.drawable.ic_category_carpenter
    CategoryIds.PAINTER -> R.drawable.ic_category_format_paint
    CategoryIds.CLEANING -> R.drawable.ic_category_cleaning_services
    CategoryIds.APPLIANCE_REPAIR -> R.drawable.ic_category_home_repair_service
    CategoryIds.AC_REPAIR -> R.drawable.ic_category_ac_unit
    CategoryIds.PEST_CONTROL -> R.drawable.ic_category_pest_control
    else -> R.drawable.ic_category_other
}

@StringRes
private fun categoryLabelRes(categoryId: String): Int? = when (categoryId) {
    CategoryIds.ELECTRICIAN -> R.string.core_ui_category_electrician
    CategoryIds.PLUMBER -> R.string.core_ui_category_plumber
    CategoryIds.CARPENTER -> R.string.core_ui_category_carpenter
    CategoryIds.PAINTER -> R.string.core_ui_category_painter
    CategoryIds.CLEANING -> R.string.core_ui_category_cleaning
    CategoryIds.APPLIANCE_REPAIR -> R.string.core_ui_category_appliance_repair
    CategoryIds.AC_REPAIR -> R.string.core_ui_category_ac_repair
    CategoryIds.PEST_CONTROL -> R.string.core_ui_category_pest_control
    else -> null
}

@Composable
fun categoryLabel(category: ServiceCategory): String =
    categoryLabelRes(category.id)?.let { stringResource(it) } ?: category.name
