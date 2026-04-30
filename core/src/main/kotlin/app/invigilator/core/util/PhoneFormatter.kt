package app.invigilator.core.util

/**
 * Normalizes Indian phone numbers to E.164 format (+91XXXXXXXXXX).
 * Accepted inputs:
 *   "9876543210"         (10 digits)
 *   "+919876543210"      (E.164)
 *   "+91 9876 543 210"   (E.164 with spaces)
 *   "919876543210"       (12 digits, 91 prefix)
 */
object PhoneFormatter {

    private const val COUNTRY_CODE = "91"
    private const val EXPECTED_SUBSCRIBER_LEN = 10

    fun normalize(input: String): Result<String> {
        val digits = input.filter { it.isDigit() }
        return when {
            digits.length == EXPECTED_SUBSCRIBER_LEN ->
                Result.success("+$COUNTRY_CODE$digits")

            digits.length == EXPECTED_SUBSCRIBER_LEN + 2 && digits.startsWith(COUNTRY_CODE) ->
                Result.success("+$digits")

            else ->
                Result.failure(IllegalArgumentException("Invalid Indian phone number: $input"))
        }
    }
}
