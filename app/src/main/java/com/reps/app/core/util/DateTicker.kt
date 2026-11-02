package com.reps.app.core.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Today's date, emitted now and again just after each midnight. ViewModels that
 * captured `LocalDate.now()` once showed yesterday's "today" until they were
 * recreated; collecting this keeps Home, Progress and Nutrition on the right day.
 */
object DateTicker {

    fun today(): Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now())
            val now = LocalDateTime.now()
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
            delay(Duration.between(now, nextMidnight).toMillis() + 1_000)
        }
    }.distinctUntilChanged()
}
