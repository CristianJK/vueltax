package app.vueltax.backend.rates

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds

@Serializable
data class SourceResult(val source: String, val ok: Boolean, val saved: Int, val attempts: Int)

/** Descarga las tasas de cada fuente, con reintentos, y las guarda con su fuente. */
class RatesUpdater(
    private val sources: List<RateSource>,
    private val repository: RateRepository,
    private val maxAttempts: Int = 4,
    private val initialBackoff: kotlin.time.Duration = 2.seconds,
) {
    private val log = LoggerFactory.getLogger(RatesUpdater::class.java)

    /** Una fuente caída no impide guardar las demás. */
    suspend fun runOnce(): List<SourceResult> = sources.map { update(it) }

    private suspend fun update(source: RateSource): SourceResult {
        var backoff = initialBackoff
        for (attempt in 1..maxAttempts) {
            try {
                val saved = repository.save(source.fetch())
                log.info("Tasas de {}: {} guardadas (intento {})", source.name, saved, attempt)
                return SourceResult(source.name, ok = true, saved = saved, attempts = attempt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Tasas de {}: intento {}/{} falló: {}", source.name, attempt, maxAttempts, e.toString())
                if (attempt < maxAttempts) {
                    delay(backoff)
                    backoff *= 2
                }
            }
        }
        log.error("Tasas de {}: sin datos tras {} intentos", source.name, maxAttempts)
        return SourceResult(source.name, ok = false, saved = 0, attempts = maxAttempts)
    }

    /** Actualiza al arrancar y luego cada día a [timeUtc]. No retorna hasta que se cancela. */
    suspend fun runDaily(timeUtc: LocalTime) {
        while (true) {
            runOnce()
            delay(untilNext(timeUtc, OffsetDateTime.now(ZoneOffset.UTC)).toMillis())
        }
    }

    companion object {
        fun untilNext(timeUtc: LocalTime, now: OffsetDateTime): Duration {
            val todayRun = now.toLocalDate().atTime(timeUtc).atOffset(ZoneOffset.UTC)
            val next = if (todayRun.isAfter(now)) todayRun else todayRun.plusDays(1)
            return Duration.between(now, next)
        }
    }
}
