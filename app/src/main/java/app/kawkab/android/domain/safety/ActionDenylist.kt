package app.kawkab.android.domain.safety

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which of the three denylist layers refused an action.
 */
enum class BlockLayer {
    /** A shipped package, or a Settings security page. */
    PACKAGE,

    /** FLAG_SECURE, a password field, or payment-looking text. */
    HEURISTIC,

    /** An intent or deep link aimed at a denied package or a payment URI. */
    INTENT,
}

/**
 * Why an action was handed back to the owner.
 *
 * @property layer Which check fired.
 * @property why A short sentence the owner can read.
 */
data class BlockReason(val layer: BlockLayer, val why: String)

/**
 * The screen the accessibility service last observed.
 *
 * @property packageName Foreground package, empty when it is unknown.
 * @property secure True when the window is accessibility-data-sensitive, the API 34 signal for FLAG_SECURE.
 * @property passwordField True when a node reports itself as a password.
 * @property visibleText Text and content descriptions currently on screen.
 */
data class ScreenSnapshot(
    val packageName: String,
    val secure: Boolean,
    val passwordField: Boolean,
    val visibleText: String,
) {
    /** Factory for an empty observation. */
    companion object {
        /** Nothing observed yet. Actions are not blocked on an empty screen. */
        fun idle(): ScreenSnapshot = ScreenSnapshot(
            packageName = "",
            secure = false,
            passwordField = false,
            visibleText = "",
        )
    }
}

/**
 * The latest screen, written by the accessibility service and read before every action.
 */
@Singleton
class ScreenWatch @Inject constructor() {
    /** The screen the accessibility service last published. */
    @Volatile
    var latest: ScreenSnapshot = ScreenSnapshot.idle()
}

/**
 * Package denylist, screen heuristics, and the intent guard.
 *
 * Checked on every accessibility event and again immediately before an action.
 * Editing the package set goes through [DenylistStore], which demands a device confirmation.
 *
 * @property store The editable package set.
 */
class ActionDenylist(val store: DenylistStore, private val securityPackages: Set<String>) {
    /**
     * Refuses a banking, wallet, authenticator, password-manager, or security screen.
     */
    fun inspectScreen(screen: ScreenSnapshot): BlockReason? = packageBlock(screen) ?: heuristicBlock(screen)

    private fun packageBlock(screen: ScreenSnapshot): BlockReason? {
        val pkg = screen.packageName
        if (pkg.isNotEmpty() && pkg in store.snapshot()) {
            return BlockReason(BlockLayer.PACKAGE, "Kawkab does not act inside $pkg.")
        }
        if (pkg in securityPackages && looksLikeSecurityPage(screen.visibleText)) {
            return BlockReason(BlockLayer.PACKAGE, "Kawkab does not act on a Settings security page.")
        }
        return null
    }

    private fun heuristicBlock(screen: ScreenSnapshot): BlockReason? {
        if (screen.secure) {
            return BlockReason(BlockLayer.HEURISTIC, "This window is hidden from accessibility (FLAG_SECURE).")
        }
        if (screen.passwordField) {
            return BlockReason(BlockLayer.HEURISTIC, "Kawkab does not act on a password field.")
        }
        val why = sensitiveText(screen.visibleText) ?: return null
        return BlockReason(BlockLayer.HEURISTIC, why)
    }

    /**
     * Refuses an intent whose package is denied or whose URI is a payment link.
     */
    fun inspectIntent(targetPackage: String?, uri: String?): BlockReason? {
        if (!targetPackage.isNullOrBlank() && targetPackage in store.snapshot()) {
            return BlockReason(BlockLayer.INTENT, "That intent targets $targetPackage.")
        }
        if (!targetPackage.isNullOrBlank() && targetPackage in securityPackages) {
            return BlockReason(BlockLayer.INTENT, "That intent targets a Settings security page.")
        }
        if (!uri.isNullOrBlank() && isPaymentUri(uri)) {
            return BlockReason(BlockLayer.INTENT, "That link is a payment or checkout URI.")
        }
        return null
    }

    /** Parsers and the text heuristics. */
    companion object {
        private val securityPage = Regex(
            "(?i)security|password|fingerprint|screen lock|encryption|credential|\\bpin\\b|" +
                "كلمة المرور|البصمة|الأمان",
        )
        private val iban = Regex("""\b[A-Z]{2}\d{2}[A-Z0-9]{11,30}\b""")
        private val cardCandidate = Regex("""(?:\d[ -]?){13,19}""")
        private val otp = Regex(
            "(?i)(otp|one-time|verification code|رمز التحقق|رمز)(\\D{0,12})(\\d{4,8})",
        )
        private val checkout = Regex(
            "(?i)checkout|pay now|add card|payment method|confirm order|الدفع|ادفع",
        )

        /**
         * Parses `safety/package_denylist.txt`. A `security-pages` suffix is the Settings rule.
         */
        fun fromText(raw: String): ActionDenylist {
            val packages = mutableSetOf<String>()
            val security = mutableSetOf<String>()
            raw.lines().forEach { line ->
                val trimmed = line.substringBefore("#").trim()
                if (trimmed.isEmpty()) return@forEach
                if (trimmed.endsWith("security-pages")) {
                    security += trimmed.removeSuffix("security-pages").trim()
                } else {
                    packages += trimmed
                }
            }
            return ActionDenylist(DenylistStore(packages), security)
        }

        private fun looksLikeSecurityPage(text: String): Boolean = securityPage.containsMatchIn(text)

        private fun sensitiveText(text: String): String? {
            if (text.isBlank()) return null
            return ibanReason(text) ?: cardReason(text) ?: otpReason(text) ?: checkoutReason(text)
        }

        private fun ibanReason(text: String): String? =
            if (iban.containsMatchIn(text)) "The screen shows an IBAN." else null

        private fun cardReason(text: String): String? {
            val hit = cardCandidate.findAll(text).any { candidate -> luhn(candidate.value.filter { it.isDigit() }) }
            return if (hit) "The screen shows a card number." else null
        }

        private fun otpReason(text: String): String? =
            if (otp.containsMatchIn(text)) "The screen shows a one-time code." else null

        private fun checkoutReason(text: String): String? =
            if (checkout.containsMatchIn(text)) "The screen looks like a checkout." else null

        private fun luhn(digits: String): Boolean {
            if (digits.length !in CARD_MIN..CARD_MAX) return false
            var sum = 0
            var alternate = false
            for (index in digits.indices.reversed()) {
                var digit = digits[index].digitToInt()
                if (alternate) {
                    digit *= 2
                    if (digit > NINE) digit -= NINE
                }
                sum += digit
                alternate = !alternate
            }
            return sum % TEN == 0
        }

        /**
         * True for payment schemes and checkout links. `tel:` and `smsto:` are not payment URIs.
         */
        fun isPaymentUri(uri: String): Boolean {
            val lower = uri.lowercase()
            if (lower.startsWith("upi:") || lower.startsWith("payto:")) return true
            if (lower.contains("paypal.com") || lower.contains("pay.google.com")) return true
            return lower.contains("checkout")
        }

        private const val CARD_MIN = 13
        private const val CARD_MAX = 19
        private const val NINE = 9
        private const val TEN = 10
    }
}
