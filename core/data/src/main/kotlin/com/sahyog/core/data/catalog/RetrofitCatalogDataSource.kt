package com.sahyog.core.data.catalog

import com.sahyog.core.data.network.SahyogApi
import javax.inject.Inject

internal class RetrofitCatalogDataSource @Inject constructor(
    private val api: SahyogApi,
) : CatalogNetworkDataSource {
    override suspend fun fetchCatalog(): NetworkCatalog = api.catalog()
}
