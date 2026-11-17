package com.example.mcp.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks personal data before text is returned to a model. Log lines and exception messages often carry
 * email addresses and account or card numbers that the model does not need in order to debug.
 */
public final class PiiMasker {

    private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    // Card numbers written in groups of four, e.g. "4111 1111 1111 1111" or "4111-1111-1111-1111".
    private static final Pattern GROUPED_CARD = Pattern.compile(
        "(?<![A-Za-z0-9-])\\d{4}[ -]\\d{4}[ -]\\d{4}[ -](\\d{4})(?![A-Za-z0-9-])");
    private static final Pattern LONG_NUMBER = Pattern.compile("(?<![A-Za-z0-9])\\d{5,}(\\d{4})(?![A-Za-z0-9])");

    private PiiMasker() {}

    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher email = EMAIL.matcher(text);
        String masked = email.replaceAll("$1***@$2");
        masked = GROUPED_CARD.matcher(masked).replaceAll("****$1");
        return LONG_NUMBER.matcher(masked).replaceAll("****$1");
    }
}
