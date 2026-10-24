package com.ronrob.brti.feed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure, deterministic technical calculations for finalized exchange candles.
 * No trades, settlement probabilities, or Kalshi/BRTI price substitution.
 * Uses Wilder RSI, Wilder ATR, and Wilder +DI/-DI/ADX.
 */
public final class BtcTechnicalMath {

    private BtcTechnicalMath() {}

    public record Candle(
        long startEpochSeconds,
        double open,
        double high,
        double low,
        double close,
        double vwap,
        double volume,
        long tradeCount
    ) {}

    public static Map<String, Object> analyze(
        List<Candle> candles,
        int intervalMinutes,
        Instant evaluatedAt
    ) {
        if (intervalMinutes <= 0) throw new IllegalArgumentException("Invalid timeframe");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("intervalMinutes", intervalMinutes);
        out.put("closedCandleCount", candles.size());
        out.put(
            "indicatorPolicy",
            "All calculations use finalized Kraken BTC/USD OHLC candles. No forming candle or settlement-price substitution."
        );
        if (candles.size() < 55) {
            out.put("status", "INSUFFICIENT_HISTORY");
            out.put("error", "Need at least 55 completed candles, got " + candles.size());
            return out;
        }

        int n = candles.size();
        Candle last = candles.get(n - 1);
        long lastCloseEpoch = last.startEpochSeconds() + (long) intervalMinutes * 60;
        long secondsSinceClose = Math.max(0, evaluatedAt.getEpochSecond() - lastCloseEpoch);
        boolean fresh =
            lastCloseEpoch <= evaluatedAt.getEpochSecond() + 5 &&
            secondsSinceClose <= Math.max(150L, (long) intervalMinutes * 120L + 30L);

        double[] closes = new double[n];
        for (int i = 0; i < n; i++) closes[i] = candles.get(i).close();
        double ema8 = ema(closes, 8);
        double ema21 = ema(closes, 21);
        double ema50 = ema(closes, 50);
        double rsi = rsi14(closes);
        Directional d = directional14(candles);

        // Kraken's OHLC VWAP is per-bar, not a daily anchored/session VWAP.
        // Rolling 20 closed bars only; volume-weighted from per-bar exchange VWAP.
        int from = Math.max(0, n - 20);
        double totalWeighted = 0,
            totalVolume = 0;
        for (int i = from; i < n; i++) {
            Candle c = candles.get(i);
            if (c.volume() > 0 && c.vwap() > 0) {
                totalWeighted += c.vwap() * c.volume();
                totalVolume += c.volume();
            }
        }
        Double rollingVwap = totalVolume > 0 ? totalWeighted / totalVolume : null;

        double priorHigh = Double.NEGATIVE_INFINITY;
        double priorLow = Double.POSITIVE_INFINITY;
        // Reference is the PREVIOUS 20 closed candles, excluding the latest closed bar.
        for (int i = Math.max(0, n - 21); i < n - 1; i++) {
            priorHigh = Math.max(priorHigh, candles.get(i).high());
            priorLow = Math.min(priorLow, candles.get(i).low());
        }
        String trend = "MIXED";
        if (last.close() > ema8 && ema8 > ema21 && ema21 > ema50) trend = "UPTREND";
        else if (last.close() < ema8 && ema8 < ema21 && ema21 < ema50) trend = "DOWNTREND";
        String momentum = "MIXED";
        if (rsi >= 55 && d.plusDi > d.minusDi && d.adx >= 20) momentum = "POSITIVE";
        else if (rsi <= 45 && d.minusDi > d.plusDi && d.adx >= 20) momentum = "NEGATIVE";

        out.put("status", fresh ? "READY" : "STALE");
        out.put("fresh", fresh);
        out.put(
            "lastClosedCandleStartUtc",
            Instant.ofEpochSecond(last.startEpochSeconds()).toString()
        );
        out.put("lastClosedCandleEndUtc", Instant.ofEpochSecond(lastCloseEpoch).toString());
        out.put("lastClosedCandleEndEpochSeconds", lastCloseEpoch);
        out.put("secondsSinceLastClose", secondsSinceClose);
        out.put("open", num(last.open()));
        out.put("high", num(last.high()));
        out.put("low", num(last.low()));
        out.put("close", num(last.close()));
        out.put("volumeBtc", num(last.volume()));
        out.put("tradeCount", last.tradeCount());
        out.put("ema8", num(ema8));
        out.put("ema21", num(ema21));
        out.put("ema50", num(ema50));
        out.put("rsi14", num(rsi));
        out.put("atr14", num(d.atr));
        out.put("adx14", num(d.adx));
        out.put("plusDi14", num(d.plusDi));
        out.put("minusDi14", num(d.minusDi));
        out.put("rollingVwap20ClosedBars", rollingVwap == null ? null : num(rollingVwap));
        out.put(
            "rollingVwapExplanation",
            "Kraken per-bar VWAP weighted by volume for the last 20 completed candles, NOT an anchored/session VWAP."
        );
        out.put("previous20High", num(priorHigh));
        out.put("previous20Low", num(priorLow));
        out.put("trendStructure", trend);
        out.put("momentumContext", momentum);
        out.put("priceVsEma21Pct", num(100.0 * (last.close() / ema21 - 1.0)));
        out.put("atr14PctOfClose", num((100.0 * d.atr) / last.close()));
        List<Double> recent = new ArrayList<>();
        for (int i = Math.max(0, n - 48); i < n; i++) recent.add(num(closes[i]));
        out.put("recent48ClosedPrices", recent);
        out.put(
            "warning",
            "Technical context only. Kraken spot candles are not BRTI and cannot by themselves estimate Kalshi win probability or guarantee an entry."
        );
        return out;
    }

    private static Double num(double v) {
        return Double.isFinite(v)
            ? BigDecimal.valueOf(v).setScale(6, RoundingMode.HALF_UP).doubleValue()
            : null;
    }

    static double ema(double[] prices, int period) {
        if (prices.length < period) throw new IllegalArgumentException("Not enough prices");
        double sum = 0;
        for (int i = 0; i < period; i++) sum += prices[i];
        double average = sum / period;
        double smoothingFactor = 2.0 / (period + 1);
        for (int i = period; i < prices.length; i++) average =
            prices[i] * smoothingFactor + average * (1 - smoothingFactor);
        return average;
    }

    static double rsi14(double[] prices) {
        if (prices.length < 15) throw new IllegalArgumentException("Not enough prices");
        double gains = 0,
            losses = 0;
        for (int i = 1; i <= 14; i++) {
            double delta = prices[i] - prices[i - 1];
            gains += Math.max(0, delta);
            losses += Math.max(0, -delta);
        }
        gains /= 14;
        losses /= 14;
        for (int i = 15; i < prices.length; i++) {
            double delta = prices[i] - prices[i - 1];
            gains = (gains * 13 + Math.max(0, delta)) / 14;
            losses = (losses * 13 + Math.max(0, -delta)) / 14;
        }
        if (gains == 0 && losses == 0) return 50;
        if (losses == 0) return 100;
        return 100 - 100 / (1 + gains / losses);
    }

    private record Directional(double atr, double plusDi, double minusDi, double adx) {}

    private static Directional directional14(List<Candle> bars) {
        int n = bars.size();
        double[] trueRanges = new double[n],
            positiveMovements = new double[n],
            negativeMovements = new double[n];
        for (int i = 1; i < n; i++) {
            Candle p = bars.get(i - 1),
                c = bars.get(i);
            trueRanges[i] = Math.max(
                c.high() - c.low(),
                Math.max(Math.abs(c.high() - p.close()), Math.abs(c.low() - p.close()))
            );
            double up = c.high() - p.high();
            double down = p.low() - c.low();
            positiveMovements[i] = up > down && up > 0 ? up : 0;
            negativeMovements[i] = down > up && down > 0 ? down : 0;
        }
        double trueRangeSum = 0,
            positiveMovementSum = 0,
            negativeMovementSum = 0;
        for (int i = 1; i <= 14; i++) {
            trueRangeSum += trueRanges[i];
            positiveMovementSum += positiveMovements[i];
            negativeMovementSum += negativeMovements[i];
        }
        double lastPlus = 0,
            lastMinus = 0,
            adx = 0,
            dxSum = 0;
        int dxSamples = 0;
        for (int i = 14; i < n; i++) {
            if (i > 14) {
                trueRangeSum = trueRangeSum - trueRangeSum / 14 + trueRanges[i];
                positiveMovementSum =
                    positiveMovementSum - positiveMovementSum / 14 + positiveMovements[i];
                negativeMovementSum =
                    negativeMovementSum - negativeMovementSum / 14 + negativeMovements[i];
            }
            lastPlus = trueRangeSum == 0 ? 0 : (100 * positiveMovementSum) / trueRangeSum;
            lastMinus = trueRangeSum == 0 ? 0 : (100 * negativeMovementSum) / trueRangeSum;
            double sum = lastPlus + lastMinus;
            double dx = sum == 0 ? 0 : (100 * Math.abs(lastPlus - lastMinus)) / sum;
            if (dxSamples < 14) {
                dxSum += dx;
                dxSamples++;
                if (dxSamples == 14) adx = dxSum / 14;
            } else {
                adx = (adx * 13 + dx) / 14;
            }
        }
        return new Directional(trueRangeSum / 14, lastPlus, lastMinus, adx);
    }
}
