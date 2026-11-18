package com.sahyog.core.common.dispatcher

import javax.inject.Qualifier

/**
 * Inject dispatchers instead of calling Dispatchers.IO directly, so tests can
 * swap in a TestDispatcher and run coroutine code deterministically.
 *
 * Usage: `class FooRepository @Inject constructor(@Dispatcher(IO) private val io: CoroutineDispatcher)`
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: SahyogDispatcher)

enum class SahyogDispatcher {
    Default,
    IO,
}
