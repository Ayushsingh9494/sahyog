package com.sahyog.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** The Sahyog API: the single source of truth for users, workers, bookings and reviews. */
@SpringBootApplication
class SahyogServerApplication

fun main(args: Array<String>) {
    runApplication<SahyogServerApplication>(*args)
}
