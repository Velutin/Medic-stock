package com.project.mss.util;

/**
 * The NF-e access key: 44 digits that describe the invoice without consulting anything.
 *
 * <pre>
 *   cUF(2) AAMM(4) CNPJ(14) mod(2) serie(3) nNF(9) tpEmis(1) cNF(8) cDV(1)
 * </pre>
 *
 * The last digit checks the other 43 (modulus 11, weights 2..9 from the right), which is what makes the
 * printed barcode safe to scan: a misread digit fails the check instead of pointing at another invoice.
 */
public final class NfeAccessKey {

    private final String digits;

    private NfeAccessKey(String digits) {
        this.digits = digits;
    }

    /** Key from any text that carries 44 digits (spaces and punctuation are ignored); null when invalid. */
    public static NfeAccessKey parse(String text) {
        if (text == null) return null;
        String digits = text.replaceAll("\\D", "");
        if (digits.length() != 44 || !isCheckDigitValid(digits)) return null;
        return new NfeAccessKey(digits);
    }

    public static boolean isValid(String text) {
        return parse(text) != null;
    }

    public String digits() {
        return digits;
    }

    public String emitterCnpj() {
        return digits.substring(6, 20);
    }

    /** Series without leading zeros ("002" is series 2). */
    public String series() {
        return stripZeros(digits.substring(22, 25));
    }

    /** Invoice number without leading zeros ("000242466" is 242466). */
    public String number() {
        return stripZeros(digits.substring(25, 34));
    }

    /** Model: 55 for the NF-e, 65 for the consumer NFC-e. */
    public String model() {
        return digits.substring(20, 22);
    }

    private static String stripZeros(String value) {
        String stripped = value.replaceFirst("^0+", "");
        return stripped.isEmpty() ? "0" : stripped;
    }

    /** Modulus 11 over the first 43 digits, weights 2 to 9 cycling from the right. */
    private static boolean isCheckDigitValid(String digits) {
        int sum = 0;
        int weight = 2;
        for (int i = 42; i >= 0; i--) {
            sum += (digits.charAt(i) - '0') * weight;
            weight = weight == 9 ? 2 : weight + 1;
        }
        int remainder = sum % 11;
        int expected = (remainder == 0 || remainder == 1) ? 0 : 11 - remainder;
        return expected == digits.charAt(43) - '0';
    }
}
