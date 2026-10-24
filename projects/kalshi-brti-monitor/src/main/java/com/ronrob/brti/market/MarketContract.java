package com.ronrob.brti.market;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/** Public contract metadata. Timestamp text is retained for the existing JSON response. */
public record MarketContract(
    String ticker,
    String title,
    String status,
    String openTime,
    String closeTime,
    BigDecimal priceToBeat,
    BigDecimal yesAskDollars,
    BigDecimal noAskDollars,
    BigDecimal volumeFp,
    BigDecimal openInterestFp,
    String updatedTime
) {
    public static final String SERIES = "KXBTC15M";
    public static final Duration WINDOW = Duration.ofMinutes(15);

    /** A future contract can be tradable before its actual 15-minute settlement window. */
    public boolean isCurrentAt(Instant now) {
        if (!"open".equals(status) && !"active".equals(status)) return false;
        Instant close = parseInstant(closeTime);
        Instant open = parseInstant(openTime);
        return (
            close != null &&
            !now.isBefore(close.minus(WINDOW)) &&
            now.isBefore(close) &&
            (open == null || !now.isBefore(open))
        );
    }

    public static Instant parseInstant(String value) {
        try {
            return value == null || value.isBlank() ? null : Instant.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
