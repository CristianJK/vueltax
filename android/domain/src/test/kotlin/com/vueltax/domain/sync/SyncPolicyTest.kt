package com.vueltax.domain.sync

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPolicyTest {
    private val today = LocalDate.of(2026, 11, 12)

    @Test
    fun `descarga las reglas si no hay ninguna guardada`() {
        assertTrue(SyncPolicy.shouldDownloadRules(cachedVersion = null, remoteVersion = 1))
    }

    @Test
    fun `descarga las reglas si la version del servidor es distinta`() {
        assertTrue(SyncPolicy.shouldDownloadRules(cachedVersion = 1, remoteVersion = 2))
        assertTrue(SyncPolicy.shouldDownloadRules(cachedVersion = 3, remoteVersion = 2))
    }

    @Test
    fun `no descarga las reglas si la version es la misma`() {
        assertFalse(SyncPolicy.shouldDownloadRules(cachedVersion = 2, remoteVersion = 2))
    }

    @Test
    fun `no descarga las reglas si el servidor aun no publica ninguna`() {
        assertFalse(SyncPolicy.shouldDownloadRules(cachedVersion = null, remoteVersion = null))
        assertFalse(SyncPolicy.shouldDownloadRules(cachedVersion = 1, remoteVersion = null))
    }

    @Test
    fun `descarga las tasas si nunca se han descargado o son de un dia anterior`() {
        assertTrue(SyncPolicy.shouldDownloadRates(lastDownload = null, today = today))
        assertTrue(SyncPolicy.shouldDownloadRates(lastDownload = today.minusDays(1), today = today))
    }

    @Test
    fun `no descarga las tasas dos veces el mismo dia`() {
        assertFalse(SyncPolicy.shouldDownloadRates(lastDownload = today, today = today))
    }
}
