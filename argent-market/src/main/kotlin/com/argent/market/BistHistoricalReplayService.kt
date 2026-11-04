package com.argent.market

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Replays caller-supplied BIST history into the market ticker stream. */
internal class BistHistoricalReplayService(
    private val scope: CoroutineScope,
    private val publish: suspend (TickerUpdate) -> Unit,
) {
    private val active = AtomicBoolean(false)

    @Volatile private var currentStatus: BistReplayStatus? = null

    fun start(
        csv: String,
        speed: Int,
    ): BistReplayStatus {
        require(speed in 1..100_000) { "speed must be between 1 and 100000" }
        val rows = BistCsvParser.parse(csv)
        check(active.compareAndSet(false, true)) { "A BIST historical replay is already running" }
        val replayId = UUID.randomUUID().toString()
        currentStatus = BistReplayStatus(replayId, "RUNNING", rows.size, 0)
        scope.launch {
            try {
                var previousTimestamp: Long? = null
                rows.forEachIndexed { index, row ->
                    previousTimestamp?.let { previous ->
                        val scaledDelay =
                            ((row.timestamp - previous).coerceAtLeast(0) / speed).coerceAtMost(5_000)
                        if (scaledDelay > 0) delay(scaledDelay)
                    }
                    previousTimestamp = row.timestamp
                    publish(
                        TickerUpdate(
                            row.symbol,
                            row.price,
                            volume24h = row.volume24h,
                            source = "bist-csv-replay",
                            timestamp = row.timestamp,
                        ),
                    )
                    currentStatus = BistReplayStatus(replayId, "RUNNING", rows.size, index + 1)
                }
                currentStatus = BistReplayStatus(replayId, "COMPLETED", rows.size, rows.size)
            } catch (cancelled: CancellationException) {
                currentStatus =
                    BistReplayStatus(replayId, "CANCELLED", rows.size, currentStatus?.processed ?: 0)
                throw cancelled
            } catch (failure: Exception) {
                currentStatus =
                    BistReplayStatus(
                        replayId,
                        "FAILED",
                        rows.size,
                        currentStatus?.processed ?: 0,
                        failure.message ?: "Replay failed",
                    )
            } finally {
                active.set(false)
            }
        }
        return currentStatus ?: error("BIST replay finished without a result")
    }

    fun status(): BistReplayStatus? = currentStatus

    fun isActive(): Boolean = active.get()
}
