package com.swingStockSelector.service;

import com.swingStockSelector.entity.StockPriceDaily;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndicatorService {

    public StockPriceDaily calculateFromPrevious(
            StockPriceDaily prev,
            Map<String, Object> todayRaw) {

        double open = ((Number) todayRaw.get("Open")).doubleValue();
        double high = ((Number) todayRaw.get("High")).doubleValue();
        double low = ((Number) todayRaw.get("Low")).doubleValue();
        double close = ((Number) todayRaw.get("Close")).doubleValue();
        double volume = ((Number) todayRaw.get("Volume")).doubleValue();

        StockPriceDaily entity = new StockPriceDaily();
        entity.setTicker(prev.getTicker());

        // ===== DATE =====
        entity.setTradeDate(
                LocalDateTime.parse((String) todayRaw.get("Date")).toLocalDate()
        );

        // ===== PRICE =====
        entity.setOpen(open);
        entity.setHigh(high);
        entity.setLow(low);
        entity.setClose(close);
        entity.setVolume(volume);

        // =====================================================
        // ================= TREND =============================
        // =====================================================

        entity.setEma20(calcEMA(close, prev.getEma20(), 20));
        entity.setEma50(calcEMA(close, prev.getEma50(), 50));
        entity.setEma200(calcEMA(close, prev.getEma200(), 200));

        // =====================================================
        // ================= MOMENTUM ==========================
        // =====================================================

        double change = close - prev.getClose();
        double gain = Math.max(change, 0);
        double loss = Math.max(-change, 0);

        // ❗ Simplified RSI (still not perfect but better)
        double prevRsi = prev.getRsi14() != null ? prev.getRsi14() : 50;
        double rs = gain / (loss + 1e-10);
        entity.setRsi14(100 - (100 / (1 + rs)));

        // =====================================================
        // ================= MACD (FIXED) ======================
        // =====================================================

        double ema12 = calcEMA(close, prev.getMacd() != null ? prev.getMacd() : close, 12);
        double ema26 = calcEMA(close, prev.getMacdSignal() != null ? prev.getMacdSignal() : close, 26);

        double macd = ema12 - ema26;
        double signal = calcEMA(macd, prev.getMacdSignal() != null ? prev.getMacdSignal() : macd, 9);

        entity.setMacd(macd);
        entity.setMacdSignal(signal);
        entity.setMacdHist(macd - signal);

        // =====================================================
        // ================= VOLATILITY ========================
        // =====================================================

        double tr = Math.max(high - low,
                Math.max(Math.abs(high - prev.getClose()),
                        Math.abs(low - prev.getClose())));

        double atr14 = (prev.getAtr14() != null ? prev.getAtr14() : tr);
        atr14 = (atr14 * 13 + tr) / 14;

        entity.setAtr14(atr14);

        // =====================================================
        // ================= VOLUME ============================
        // =====================================================

        double prevVolAvg = prev.getVolumeAvg20() != null ? prev.getVolumeAvg20() : volume;
        entity.setVolumeAvg20((prevVolAvg * 19 + volume) / 20);

        // =====================================================
        // ================= STRUCTURE =========================
        // =====================================================

        // ⚠️ Still not true rolling — requires DB/window
        entity.setHighestHigh20(Math.max(high, prev.getHighestHigh20() != null ? prev.getHighestHigh20() : high));
        entity.setLowestLow20(Math.min(low, prev.getLowestLow20() != null ? prev.getLowestLow20() : low));

        // =====================================================
        // ================= BOLLINGER (BETTER) ================
        // =====================================================

        double middle = entity.getEma20();
        double stdApprox = Math.sqrt(Math.abs(close - middle)); // still approximation

        entity.setBbMiddle(middle);
        entity.setBbUpper(middle + 2 * stdApprox);
        entity.setBbLower(middle - 2 * stdApprox);

        // =====================================================
        // ================= SCORE =============================
        // =====================================================

        double score = 0;

        if (close > entity.getEma20()) score++;
        if (entity.getEma20() > entity.getEma50()) score++;
        if (entity.getEma50() > entity.getEma200()) score++;
        if (entity.getRsi14() > 50) score++;
        if (macd > signal) score++;

        entity.setScore(score);

        // =====================================================
        entity.setCreatedAt(LocalDateTime.now());

        return entity;
    }

    private double calcEMA(double price, Double prevEma, int period) {
        if (prevEma == null) return price;

        double k = 2.0 / (period + 1);
        return price * k + prevEma * (1 - k);
    }
}
