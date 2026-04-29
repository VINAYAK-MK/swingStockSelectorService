package com.swingStockSelector.service.strategies;

import com.swingStockSelector.entity.StockPriceDaily;
import com.swingStockSelector.model.StrategyParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BreakoutStrategy implements TradingStrategy {

    @Override
    public boolean shouldEnter(List<StockPriceDaily> candles, int i, StrategyParams params) {

        // Safety check (avoid index issues)
        if (i < params.getBreakoutLookback() || i >= candles.size() - 1) {
            return false;
        }

        StockPriceDaily today = candles.get(i);

        int lookback = params.getBreakoutLookback();

        double highestHigh = Double.MIN_VALUE;

        for (int j = i - lookback; j < i; j++) {
            highestHigh = Math.max(highestHigh, candles.get(j).getHigh());
        }

        // 1. Breakout with buffer
        double breakoutBuffer = 0.98;
        boolean breakout = today.getClose() >= highestHigh * breakoutBuffer;

        // 2. Volume confirmation
        boolean volume =
                today.getVolume() >= today.getVolumeAvg20();

        // 3. Volatility filter
        boolean volatility =
                today.getAtr14() >= params.getMinAtr() * 0.9;

        // 4. Bullish candle
        boolean strongCandle =
                today.getClose() > today.getOpen();

        // -------------------------------
        // NEW PEAK-AVOIDANCE FILTERS
        // -------------------------------

        // 5. Avoid overextended breakout
        double distance = today.getClose() / highestHigh;
        boolean notTooExtended = distance <= 1.02;

        // 6. Avoid exhaustion candles
        double candleRange = today.getHigh() - today.getLow();
        boolean notExhausted = candleRange <= today.getAtr14() * 1.5;

        // 7. RSI filter (avoid overbought)
        boolean rsiSafe = today.getRsi14() <= 65;

        // 8. Avoid recent spike (previous candle already breakout)
        boolean recentSpike =
                candles.get(i - 1).getHigh() > highestHigh * 1.02;

        // 9. Confirmation (next candle should not reverse)
        boolean confirmation =
                today.getClose() > today.getOpen() &&
                        (today.getClose() - today.getLow()) >= (today.getHigh() - today.getClose());

        // -------------------------------
        // Score system
        // -------------------------------
        int score = 0;
        if (breakout) score++;
        if (volume) score++;
        if (volatility) score++;
        if (strongCandle) score++;

        // -------------------------------
        // FINAL DECISION
        // -------------------------------
        return score >= 3
                && notTooExtended
                && notExhausted
                && rsiSafe
                && !recentSpike
                && confirmation;
    }
}