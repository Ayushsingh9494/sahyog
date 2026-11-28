package com.sahyog.server.booking

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * For showing the whole flow at any hour (e.g. an evening presentation): with
 * `sahyog.demo.relax-start-window=true` a worker may set off and start a job straight away instead
 * of from [BookingRules.JOB_START_WINDOW_HOURS] before the visit. Every other rule still applies
 * (slots, start code, payments). Off by default; never meant for real use.
 */
@Component
class DemoMode(
    @Value("\${sahyog.demo.relax-start-window:false}") var relaxStartWindow: Boolean,
)
