package com.swingStockSelector.service;

import com.swingStockSelector.entity.StockPriceDaily;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndicatorService {

    private double calcEMA(double price, Double prevEma, int period) {
        if (prevEma == null) return price;

        double k = 2.0 / (period + 1);
        return price * k + prevEma * (1 - k);
    }

    public StockPriceDaily calculateFromHistory(
            List<StockPriceDaily> history,
            Map<String, Object> todayRaw) {

        StockPriceDaily prev = history.get(history.size() - 1);

        double open = ((Number) todayRaw.get("open")).doubleValue();
        double high = ((Number) todayRaw.get("high")).doubleValue();
        double low = ((Number) todayRaw.get("low")).doubleValue();
        double close = ((Number) todayRaw.get("close")).doubleValue();
        double volume = ((Number) todayRaw.get("volume")).doubleValue();

        StockPriceDaily entity = new StockPriceDaily();

        entity.setTicker(prev.getTicker());

        String rawDate = (String) todayRaw.get("tradeDate");

        entity.setTradeDate(
                rawDate.contains("T")
                        ? LocalDateTime.parse(rawDate).toLocalDate()
                        : LocalDate.parse(rawDate)
        );

        entity.setOpen(open);
        entity.setHigh(high);
        entity.setLow(low);
        entity.setClose(close);
        entity.setVolume(volume);

        // ==========================================
        // EMA
        // ==========================================

        entity.setEma20(calcEMA(close, prev.getEma20(), 20));
        entity.setEma50(calcEMA(close, prev.getEma50(), 50));
        entity.setEma200(calcEMA(close, prev.getEma200(), 200));

        // ==========================================
        // RSI
        // ==========================================

        double totalGain = 0;
        double totalLoss = 0;

        for (int i = 1; i < history.size(); i++) {

            double diff =
                    history.get(i).getClose()
                            -
                            history.get(i - 1).getClose();

            if (diff > 0) totalGain += diff;
            else totalLoss += Math.abs(diff);
        }

        double todayDiff = close - prev.getClose();

        if (todayDiff > 0) totalGain += todayDiff;
        else totalLoss += Math.abs(todayDiff);

        double avgGain = totalGain / 14.0;
        double avgLoss = totalLoss / 14.0;

        double rs = avgGain / (avgLoss + 0.0001);

        double rsi =
                100 - (100 / (1 + rs));

        entity.setRsi14(rsi);

        // ==========================================
        // MACD
        // ==========================================

        double ema12 =
                calcEMA(close,
                        prev.getMacd() != null
                                ? prev.getMacd()
                                : close,
                        12);

        double ema26 =
                calcEMA(close,
                        prev.getMacdSignal() != null
                                ? prev.getMacdSignal()
                                : close,
                        26);

        double macd = ema12 - ema26;

        double signal =
                calcEMA(
                        macd,
                        prev.getMacdSignal() != null
                                ? prev.getMacdSignal()
                                : macd,
                        9
                );

        entity.setMacd(macd);
        entity.setMacdSignal(signal);
        entity.setMacdHist(macd - signal);

        // ==========================================
        // ATR
        // ==========================================

        double tr =
                Math.max(
                        high - low,
                        Math.max(
                                Math.abs(high - prev.getClose()),
                                Math.abs(low - prev.getClose())
                        )
                );

        double prevAtr =
                prev.getAtr14() != null
                        ? prev.getAtr14()
                        : tr;

        double atr =
                ((prevAtr * 13) + tr) / 14;

        entity.setAtr14(atr);

        // ==========================================
        // Volume avg
        // ==========================================

        double totalVolume = volume;

        for (StockPriceDaily row : history) {
            totalVolume += row.getVolume();
        }

        entity.setVolumeAvg20(
                totalVolume / (history.size() + 1)
        );

        // ==========================================
        // High / Low 20
        // ==========================================

        double highestHigh = high;
        double lowestLow = low;

        for (StockPriceDaily row : history) {

            highestHigh =
                    Math.max(highestHigh, row.getHigh());

            lowestLow =
                    Math.min(lowestLow, row.getLow());
        }

        entity.setHighestHigh20(highestHigh);
        entity.setLowestLow20(lowestLow);

        // ==========================================
        // Bollinger
        // ==========================================

        List<Double> closes = new ArrayList<>();

        for (StockPriceDaily row : history) {
            closes.add(row.getClose());
        }

        closes.add(close);

        double mean =
                closes.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(close);

        double variance =
                closes.stream()
                        .mapToDouble(c ->
                                Math.pow(c - mean, 2))
                        .average()
                        .orElse(0);

        double std = Math.sqrt(variance);

        entity.setBbMiddle(mean);
        entity.setBbUpper(mean + (2 * std));
        entity.setBbLower(mean - (2 * std));

        // ==========================================
        // Score
        // ==========================================

        double score = 0;

        if (close > entity.getEma20()) score++;
        if (entity.getEma20() > entity.getEma50()) score++;
        if (entity.getEma50() > entity.getEma200()) score++;
        if (rsi > 50) score++;
        if (macd > signal) score++;

        entity.setScore(score);

        entity.setCreatedAt(LocalDateTime.now());

        return entity;
    }
}
