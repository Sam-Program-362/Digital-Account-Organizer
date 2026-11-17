package com.example.core.files

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.core.database.ServiceAssetEntity
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class FetchedWebsiteLogo(
    val assetEntity: ServiceAssetEntity,
    val sourceHost: String,
    val normalizedWebsiteUrl: String
)

data class WebsiteHttpResponse(
    val finalUrl: String,
    val statusCode: Int,
    val contentType: String?,
    val bodyBytes: ByteArray
)

fun interface WebsiteHttpTransport {
    fun get(
        url: String,
        acceptHeader: String,
        maxBytes: Int,
        dnsResolver: (String) -> Array<InetAddress>
    ): WebsiteHttpResponse
}

/**
 * Explicitly fetches, validates, and sanitizes a website's logo/icon over HTTPS.
 *
 * Security & Privacy Guardrails:
 * 1. User-triggered only — never called automatically in the background.
 * 2. HTTPS-only — rejects cleartext HTTP, embedded credentials, and non-standard ports.
 * 3. SSRF protection — resolves hostnames and rejects loopback, link-local, site-local,
 *    CGNAT, multicast, and unique-local IPv4/IPv6 ranges on every request and redirect hop.
 * 4. Untrusted payload sanitization — caps HTML scan size (256 KB) and image download size (2 MB),
 *    skips SVG/script payloads, and re-encodes accepted raster bitmaps into normalized 256x256 PNGs
 *    via [ServiceAssetStorage].
 */
class WebsiteLogoFetcher(
    private val context: Context?,
    private val assetStorage: ServiceAssetStorage,
    private val transport: WebsiteHttpTransport = HttpsUrlConnectionTransport(),
    private val dnsResolver: (String) -> Array<InetAddress> = { InetAddress.getAllByName(it) },
    private val connectivityChecker: (() -> Boolean)? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun fetchAndStoreLogo(rawWebsiteUrl: String): FetchedWebsiteLogo = withContext(ioDispatcher) {
        coroutineContext.ensureActive()

        if (!isNetworkConnected()) {
            throw IllegalStateException(
                "Device is offline. Connect to the internet to fetch a website logo, or choose an image from your device."
            )
        }

        val normalizedPageUri = normalizeAndValidateHttpsUri(rawWebsiteUrl)
        val initialHost = normalizedPageUri.host
            ?: throw IllegalArgumentException("Please enter a valid website domain or HTTPS URL.")

        validatePublicHost(initialHost, dnsResolver)

        val pageUrlString = normalizedPageUri.toString()
        val candidateUrls = LinkedHashSet<String>()

        // Step 1: Fetch the page (or direct image URL if the user pasted a direct icon link)
        val pageResponse = runCatching {
            transport.get(
                url = pageUrlString,
                acceptHeader = "text/html,application/xhtml+xml,image/png,image/webp,image/*;q=0.8",
                maxBytes = MAX_HTML_BYTES,
                dnsResolver = dnsResolver
            )
        }.getOrNull()

        val effectivePageUri = pageResponse?.finalUrl?.let {
            runCatching { normalizeAndValidateHttpsUri(it) }.getOrNull()
        } ?: normalizedPageUri
        val effectiveHost = effectivePageUri.host ?: initialHost

        if (pageResponse != null && pageResponse.statusCode in 200..299 && pageResponse.bodyBytes.isNotEmpty()) {
            val lowerType = pageResponse.contentType?.lowercase().orEmpty()
            if (lowerType.startsWith("image/") && !lowerType.contains("svg")) {
                // The entered URL itself returned a raster image
                tryImportLogoBytes(pageResponse.bodyBytes)?.let { entity ->
                    return@withContext FetchedWebsiteLogo(
                        assetEntity = entity,
                        sourceHost = effectiveHost,
                        normalizedWebsiteUrl = effectivePageUri.toString()
                    )
                }
            } else {
                val htmlSnippet = String(pageResponse.bodyBytes, Charsets.UTF_8)
                val discovered = discoverIconCandidates(effectivePageUri.toString(), htmlSnippet)
                candidateUrls.addAll(discovered)
            }
        }

        // Step 2: Standard well-known HTTPS icon locations on the origin host
        val originBase = "https://$effectiveHost"
        candidateUrls.add("$originBase/apple-touch-icon.png")
        candidateUrls.add("$originBase/favicon.ico")
        if (!effectiveHost.startsWith("www.")) {
            candidateUrls.add("https://www.$effectiveHost/apple-touch-icon.png")
            candidateUrls.add("https://www.$effectiveHost/favicon.ico")
        }

        // Step 3: Probe candidates in priority order (capped to MAX_CANDIDATE_ATTEMPTS)
        var attempts = 0
        for (candidateUrl in candidateUrls) {
            coroutineContext.ensureActive()
            if (attempts >= MAX_CANDIDATE_ATTEMPTS) break

            val validCandidateUri = runCatching { normalizeAndValidateHttpsUri(candidateUrl) }.getOrNull()
                ?: continue
            val candidateHost = validCandidateUri.host ?: continue
            if (runCatching { validatePublicHost(candidateHost, dnsResolver) }.isFailure) {
                continue
            }

            attempts++
            val iconResponse = runCatching {
                transport.get(
                    url = validCandidateUri.toString(),
                    acceptHeader = "image/png,image/webp,image/jpeg,image/x-icon,image/*;q=0.9",
                    maxBytes = MAX_LOGO_BYTES,
                    dnsResolver = dnsResolver
                )
            }.getOrNull() ?: continue

            if (iconResponse.statusCode !in 200..299 || iconResponse.bodyBytes.isEmpty()) {
                continue
            }

            val contentType = iconResponse.contentType?.lowercase().orEmpty()
            if (contentType.contains("svg") || contentType.contains("text/html")) {
                continue
            }

            val imported = tryImportLogoBytes(iconResponse.bodyBytes)
            if (imported != null) {
                return@withContext FetchedWebsiteLogo(
                    assetEntity = imported,
                    sourceHost = effectiveHost,
                    normalizedWebsiteUrl = normalizedPageUri.toString()
                )
            }
        }

        throw IllegalArgumentException(
            "Could not find a supported logo on $effectiveHost. Try another URL or choose an image from your device."
        )
    }

    private fun tryImportLogoBytes(rawBytes: ByteArray): ServiceAssetEntity? {
        runCatching {
            return assetStorage.importFromRawBytes(rawBytes)
        }
        // Fallback: if rawBytes is an .ico container that wraps a PNG stream, extract the embedded PNG
        val embeddedPng = extractEmbeddedPngFromIco(rawBytes)
        if (embeddedPng != null) {
            return runCatching {
                assetStorage.importFromRawBytes(embeddedPng)
            }.getOrNull()
        }
        return null
    }

    private fun isNetworkConnected(): Boolean {
        connectivityChecker?.let { return it.invoke() }
        val ctx = context ?: return true
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        const val MAX_HTML_BYTES = 256 * 1024 // 256 KB head scan cap
        const val MAX_LOGO_BYTES = 2 * 1024 * 1024 // 2 MB logo download cap
        const val MAX_REDIRECTS = 3
        const val MAX_CANDIDATE_ATTEMPTS = 5
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 8_000

        private val LINK_TAG_REGEX = Regex("""<link\b[^>]*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val ATTR_REGEX = Regex("""([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""")
        private val SIZE_DIMENSIONS_REGEX = Regex("""(\d+)\s*[xX]\s*(\d+)""")

        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )

        fun normalizeAndValidateHttpsUri(rawInput: String): URI {
            val trimmed = rawInput.trim()
            require(trimmed.isNotEmpty()) { "Enter a website URL first to fetch its logo." }
            require(!trimmed.any { it.isWhitespace() }) { "Website URL must not contain spaces." }

            val withScheme = when {
                trimmed.startsWith("http://", ignoreCase = true) -> {
                    throw IllegalArgumentException(
                        "Insecure HTTP URLs are not allowed. Please use https:// or enter the domain name."
                    )
                }
                trimmed.contains("://") -> trimmed
                else -> "https://$trimmed"
            }

            val parsed = try {
                URI(withScheme)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid website URL format.")
            }

            val scheme = parsed.scheme?.lowercase()
            require(scheme == "https") {
                "Only HTTPS website URLs are supported for secure logo fetching."
            }
            require(parsed.userInfo == null) {
                "Website URL must not contain embedded credentials."
            }

            val host = parsed.host?.lowercase()?.trimEnd('.')
                ?: throw IllegalArgumentException("Website URL must include a valid domain name.")
            require(host.isNotEmpty() && host.contains('.')) {
                "Please enter a complete domain name (e.g. https://example.com)."
            }
            require(
                host != "localhost" &&
                    !host.endsWith(".local") &&
                    !host.endsWith(".internal") &&
                    !host.endsWith(".localhost")
            ) {
                "Local or internal network hosts are not allowed."
            }

            val port = parsed.port
            require(port == -1 || port == 443) {
                "Non-standard HTTPS ports are not allowed for logo fetching."
            }

            val normalizedPath = if (parsed.rawPath.isNullOrEmpty()) "/" else parsed.rawPath
            return URI(
                "https",
                null,
                host,
                -1,
                normalizedPath,
                parsed.rawQuery,
                null
            )
        }

        fun validatePublicHost(host: String, dnsResolver: (String) -> Array<InetAddress>) {
            val addresses = try {
                dnsResolver(host)
            } catch (e: Exception) {
                throw IllegalArgumentException("Unable to resolve website host '$host'.")
            }
            require(addresses.isNotEmpty()) {
                "Unable to resolve website host '$host'."
            }
            for (addr in addresses) {
                require(isPublicIpAddress(addr)) {
                    "Refusing connection to private, loopback, or reserved network address."
                }
            }
        }

        fun isPublicIpAddress(address: InetAddress): Boolean {
            if (
                address.isAnyLocalAddress ||
                address.isLoopbackAddress ||
                address.isLinkLocalAddress ||
                address.isSiteLocalAddress ||
                address.isMulticastAddress
            ) {
                return false
            }
            when (address) {
                is Inet4Address -> {
                    val b = address.address
                    val b0 = b[0].toInt() and 0xFF
                    val b1 = b[1].toInt() and 0xFF
                    // 0.0.0.0/8, 10.0.0.0/8, 127.0.0.0/8
                    if (b0 == 0 || b0 == 10 || b0 == 127) return false
                    // 100.64.0.0/10 (Carrier-grade NAT)
                    if (b0 == 100 && (b1 in 64..127)) return false
                    // 169.254.0.0/16
                    if (b0 == 169 && b1 == 254) return false
                    // 172.16.0.0/12
                    if (b0 == 172 && (b1 in 16..31)) return false
                    // 192.168.0.0/16
                    if (b0 == 192 && b1 == 168) return false
                    // 240.0.0.0/4 reserved
                    if (b0 >= 240) return false
                    return true
                }
                is Inet6Address -> {
                    val b = address.address
                    val b0 = b[0].toInt() and 0xFF
                    // Unique Local Addresses fc00::/7
                    if ((b0 and 0xFE) == 0xFC) return false
                    return true
                }
                else -> return false
            }
        }

        /**
         * Parses HTML `<link>` icon candidates and returns resolved HTTPS URLs ordered by quality.
         */
        fun discoverIconCandidates(basePageUrl: String, html: String): List<String> {
            val baseUri = runCatching { normalizeAndValidateHttpsUri(basePageUrl) }.getOrNull()
                ?: return emptyList()

            // Limit scan to <head> if present to avoid unnecessary body parsing
            val headEndIndex = html.indexOf("</head>", ignoreCase = true)
            val scanRegion = if (headEndIndex > 0) html.substring(0, headEndIndex) else html

            data class ScoredCandidate(val url: String, val score: Int)
            val candidates = mutableListOf<ScoredCandidate>()

            for (match in LINK_TAG_REGEX.findAll(scanRegion)) {
                val tag = match.value
                val attrs = mutableMapOf<String, String>()
                for (attrMatch in ATTR_REGEX.findAll(tag)) {
                    val name = attrMatch.groupValues[1].lowercase()
                    val value = attrMatch.groupValues[2]
                        .ifEmpty { attrMatch.groupValues[3] }
                        .ifEmpty { attrMatch.groupValues[4] }
                        .trim()
                    attrs[name] = value
                }

                val rel = attrs["rel"]?.lowercase() ?: continue
                val href = attrs["href"]?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                val type = attrs["type"]?.lowercase().orEmpty()
                val sizes = attrs["sizes"]?.lowercase().orEmpty()

                val relTokens = rel.split(Regex("""\s+""")).toSet()
                if ("mask-icon" in relTokens) continue

                val isAppleTouch = "apple-touch-icon" in relTokens || "apple-touch-icon-precomposed" in relTokens
                val isIcon = isAppleTouch || "icon" in relTokens || "fluid-icon" in relTokens
                if (!isIcon) continue

                // Skip SVG vector icons or data: URIs
                if (href.startsWith("data:", ignoreCase = true)) continue
                if (type.contains("svg") || href.substringBefore('?').lowercase().endsWith(".svg")) {
                    continue
                }

                val resolvedHttpsUrl = resolveToHttpsUrl(baseUri, href) ?: continue

                var score = 0
                if (isAppleTouch) score += 300
                if (type == "image/png" || resolvedHttpsUrl.lowercase().contains(".png")) score += 200
                if (type == "image/webp" || resolvedHttpsUrl.lowercase().contains(".webp")) score += 150
                val maxDim = SIZE_DIMENSIONS_REGEX.findAll(sizes)
                    .mapNotNull { it.groupValues[1].toIntOrNull() }
                    .maxOrNull() ?: 0
                score += when {
                    maxDim in 128..512 -> 250
                    maxDim in 64..127 -> 180
                    maxDim in 32..63 -> 100
                    maxDim > 512 -> 120
                    else -> 50
                }

                candidates.add(ScoredCandidate(resolvedHttpsUrl, score))
            }

            return candidates
                .sortedByDescending { it.score }
                .map { it.url }
                .distinct()
        }

        private fun resolveToHttpsUrl(baseUri: URI, rawHref: String): String? {
            return runCatching {
                val resolved = when {
                    rawHref.startsWith("//") -> URI("https:$rawHref")
                    rawHref.startsWith("http://", ignoreCase = true) -> return null
                    else -> baseUri.resolve(rawHref)
                }
                normalizeAndValidateHttpsUri(resolved.toString()).toString()
            }.getOrNull()
        }

        fun extractEmbeddedPngFromIco(rawBytes: ByteArray): ByteArray? {
            if (rawBytes.size < PNG_SIGNATURE.size + 16) return null
            outer@ for (i in 0..(rawBytes.size - PNG_SIGNATURE.size)) {
                for (j in PNG_SIGNATURE.indices) {
                    if (rawBytes[i + j] != PNG_SIGNATURE[j]) continue@outer
                }
                if (i == 0) return null // Already a top-level PNG
                return rawBytes.copyOfRange(i, rawBytes.size)
            }
            return null
        }
    }
}

class HttpsUrlConnectionTransport : WebsiteHttpTransport {

    override fun get(
        url: String,
        acceptHeader: String,
        maxBytes: Int,
        dnsResolver: (String) -> Array<InetAddress>
    ): WebsiteHttpResponse {
        var currentUrl = WebsiteLogoFetcher.normalizeAndValidateHttpsUri(url).toString()
        var redirects = 0

        while (true) {
            val uri = WebsiteLogoFetcher.normalizeAndValidateHttpsUri(currentUrl)
            val host = uri.host ?: throw IllegalArgumentException("Invalid host in URL.")
            WebsiteLogoFetcher.validatePublicHost(host, dnsResolver)

            val connection = (URL(uri.toString()).openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = WebsiteLogoFetcher.CONNECT_TIMEOUT_MS
                readTimeout = WebsiteLogoFetcher.READ_TIMEOUT_MS
                useCaches = false
                setRequestProperty("Accept", acceptHeader)
                setRequestProperty("User-Agent", "DigitalAccountOrganizer/1.0 (+Android)")
            }

            try {
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    if (redirects >= WebsiteLogoFetcher.MAX_REDIRECTS) {
                        throw IllegalArgumentException("Too many redirects while looking up website logo.")
                    }
                    val location = connection.getHeaderField("Location")?.trim()
                        ?: throw IllegalArgumentException("Redirect missing Location header.")
                    val nextUri = when {
                        location.startsWith("//") -> URI("https:$location")
                        else -> uri.resolve(location)
                    }
                    currentUrl = WebsiteLogoFetcher.normalizeAndValidateHttpsUri(nextUri.toString()).toString()
                    redirects++
                    continue
                }

                val contentType = connection.contentType
                val body = if (status in 200..299) {
                    connection.inputStream.use { readBounded(it, maxBytes) }
                } else {
                    ByteArray(0)
                }

                return WebsiteHttpResponse(
                    finalUrl = currentUrl,
                    statusCode = status,
                    contentType = contentType,
                    bodyBytes = body
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun readBounded(stream: InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(8192)
        val out = ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read == -1) break
            total += read
            require(total <= maxBytes) {
                "Remote response exceeded safe size limit (${maxBytes / 1024} KB)."
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
