package com.vueltax.domain.sync

import java.time.LocalDate

/** Decide qué datos públicos hay que volver a descargar. */
object SyncPolicy {
    /** Las reglas se descargan solo si el servidor publica una versión distinta a la guardada. */
    fun shouldDownloadRules(cachedVersion: Int?, remoteVersion: Int?): Boolean =
        remoteVersion != null && remoteVersion != cachedVersion

    /** Las tasas cambian como mucho una vez al día. */
    fun shouldDownloadRates(lastDownload: LocalDate?, today: LocalDate): Boolean =
        lastDownload == null || lastDownload.isBefore(today)
}
