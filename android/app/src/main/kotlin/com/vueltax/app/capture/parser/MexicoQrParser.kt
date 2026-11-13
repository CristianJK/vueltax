package com.vueltax.app.capture.parser

/**
 * QR del CFDI de México.
 *
 * Formato: apartado D «Especificación técnica del código de barras bidimensional a
 * incorporar en la representación impresa» del Anexo 20 de la Resolución Miscelánea Fiscal
 * para 2022 (DOF 13 de enero de 2022), vigente para CFDI 4.0. El QR es la URL de
 * verificación del SAT con estos parámetros:
 *
 * ```
 * https://verificacfdi.facturaelectronica.sat.gob.mx/default.aspx
 *   ?id=[UUID]&re=[RFC emisor]&rr=[RFC receptor]&tt=[total]&fe=[8 últimos del sello]
 * ```
 *
 * El total admite hasta 18 enteros y 6 decimales. El QR **no trae el IVA ni la fecha**:
 * hay que completarlos con OCR o pedírselos al usuario.
 */
object MexicoQrParser : InvoiceQrParser {
    private const val SAT_HOST = "verificacfdi.facturaelectronica.sat.gob.mx"
    private val uuid = Regex("""[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}""")
    private val rfc = Regex("""[A-ZÑ&]{3,4}\d{6}[A-Z0-9]{3}""")

    override fun parse(raw: String): ParsedInvoice? {
        val text = raw.trim()
        val params = queryParameters(text)
        val id = params["id"]?.takeIf { uuid.matches(it) }
        val looksLikeCfdi = SAT_HOST in text.lowercase() || "re" in params
        if (id == null || !looksLikeCfdi) return null

        return ParsedInvoice(
            country = "MX",
            source = CaptureSource.QR,
            issuerTaxId = params["re"]?.uppercase()?.takeIf { rfc.matches(it) },
            total = params["tt"]?.toAmountOrNull(),
            documentKey = id.uppercase(),
        )
    }

    /** Parámetros de la URL; el valor de `fe` puede contener `=`, así que solo se corta en el primero. */
    private fun queryParameters(text: String): Map<String, String> = text
        .substringAfter('?')
        .split('&')
        .mapNotNull { pair ->
            val name = pair.substringBefore('=', "").trim().lowercase()
            if (name.isEmpty()) null else name to pair.substringAfter('=').trim()
        }
        .toMap()
}
