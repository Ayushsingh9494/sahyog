package com.sahyog.core.database.entity

import com.sahyog.core.model.GeoPoint
import com.sahyog.core.model.Paise
import com.sahyog.core.model.PriceUnit
import com.sahyog.core.model.ServiceCategory
import com.sahyog.core.model.Worker
import com.sahyog.core.model.WorkerService

fun CategoryEntity.asExternalModel() = ServiceCategory(id = id, name = name)

fun PopulatedWorker.asExternalModel() = Worker(
    id = worker.id,
    name = worker.name,
    // Room doesn't order @Relation results; restore the cooperative's ordering.
    categories = categories.sortedBy { it.position }.map { it.asExternalModel() },
    rating = worker.rating,
    ratingCount = worker.ratingCount,
    jobsCompleted = worker.jobsCompleted,
    yearsExperience = worker.yearsExperience,
    locality = worker.locality,
    about = worker.about,
    languages = worker.languages,
    services = services.sortedBy { it.position }.map { it.asExternalModel() },
    isAvailable = worker.isAvailable,
    location = worker.latitude?.let { lat -> worker.longitude?.let { lng -> GeoPoint(lat, lng) } },
)

private fun WorkerServiceEntity.asExternalModel() = WorkerService(
    name = name,
    startingPrice = Paise(pricePaise),
    // An unknown unit from a newer server falls back to the most common one instead of crashing.
    unit = PriceUnit.entries.firstOrNull { it.name == unit } ?: PriceUnit.PerVisit,
)
