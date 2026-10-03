package com.project.mss.util;

/** Validation of Brazilian identifiers used in user registration. */
public final class BrazilianDocuments {

    private BrazilianDocuments() { }

    /** Keeps only the digits (accepts "123.456.789-09" or "(71) 99999-9999"). */
    public static String digits(String value) {
        return value == null ? null : value.replaceAll("\\D", "");
    }

    /** CPF with 11 digits, not all equal, and both check digits correct. */
    public static boolean isValidCpf(String cpf) {
        String d = digits(cpf);
        if (d == null || d.length() != 11 || d.chars().distinct().count() == 1) return false;
        return checkDigit(d, 9) == d.charAt(9) - '0' && checkDigit(d, 10) == d.charAt(10) - '0';
    }

    private static int checkDigit(String d, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (d.charAt(i) - '0') * (length + 1 - i);
        }
        int rest = (sum * 10) % 11;
        return rest == 10 ? 0 : rest;
    }

    /** Mobile phone: DDD (two digits, no zero) + 9 + eight digits, e.g. 71999999999. */
    public static boolean isValidMobile(String phone) {
        String d = digits(phone);
        return d != null && d.matches("[1-9][1-9]9\\d{8}");
    }
}
