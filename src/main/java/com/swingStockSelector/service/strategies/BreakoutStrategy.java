package com.swingStockSelector.service.strategies;

import com.swingStockSelector.entity.StockPriceDaily;
import com.swingStockSelector.model.StrategyParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BreakoutStrategy implements TradingStrategy {

    @Override
    public boolean shouldEnter(List<StockPriceDaily> candles,
                               int i,
                               StrategyParams params) {

        // Safety check
        System.out.println(params);
        if (i < params.getBreakoutLookback()
                || i-1 >= candles.size() - 1) {
            return false;
        }

        StockPriceDaily today = candles.get(i);

        int lookback = params.getBreakoutLookback();

        double highestHigh = Double.MIN_VALUE;

        for (int j = i - lookback; j < i; j++) {
            highestHigh = Math.max(highestHigh, candles.get(j).getHigh());
        }

        // -------------------------------
        // PARAM VALUES (with defaults)
        // -------------------------------
        double breakoutBuffer =
                Optional.ofNullable(params.getBreakoutBuffer())
                        .orElse(0.98);

        double volumeMultiplier =
                Optional.ofNullable(params.getVolumeMultiplier())
                        .orElse(1.0);

        double minAtrMultiplier =
                Optional.ofNullable(params.getMinAtrMultiplier())
                        .orElse(0.9);

        double maxExtension =
                Optional.ofNullable(params.getMaxExtension())
                        .orElse(1.02);

        double maxCandleAtrMultiplier =
                Optional.ofNullable(params.getMaxCandleAtrMultiplier())
                        .orElse(1.5);

        double maxRsi =
                Optional.ofNullable(params.getMaxRsi())
                        .orElse(65.0);

        double recentSpikeThreshold =
                Optional.ofNullable(params.getRecentSpikeThreshold())
                        .orElse(1.02);

        int minScore =
                Optional.ofNullable(params.getMinScore())
                        .orElse(3);

        // -------------------------------
        // ENTRY CONDITIONS
        // -------------------------------

        // 1. Breakout
        boolean breakout =
                today.getClose() >= highestHigh * breakoutBuffer;

        // 2. Volume
        boolean volume =
                today.getVolume()
                        >= today.getVolumeAvg20() * volumeMultiplier;

        // 3. Volatility
        boolean volatility =
                today.getAtr14()
                        >= params.getMinAtr() * minAtrMultiplier;

        // 4. Bullish candle
        boolean strongCandle =
                today.getClose() > today.getOpen();

        // 5. Not overextended
        double distance = today.getClose() / highestHigh;

        boolean notTooExtended =
                distance <= maxExtension;

        // 6. Not exhausted
        double candleRange =
                today.getHigh() - today.getLow();

        boolean notExhausted =
                candleRange
                        <= today.getAtr14() * maxCandleAtrMultiplier;

        // 7. RSI safe
        boolean rsiSafe =
                today.getRsi14() <= maxRsi;

        // 8. No recent spike
        boolean recentSpike =
                candles.get(i - 1).getHigh()
                        > highestHigh * recentSpikeThreshold;

        // 9. Candle confirmation
        boolean confirmation =
                today.getClose() > today.getOpen()
                        &&
                        (today.getClose() - today.getLow())
                                >=
                                (today.getHigh() - today.getClose());

        // -------------------------------
        // SCORE
        // -------------------------------
        int score = 0;
        if (breakout) score++;
        if (volume) score++;
        if (volatility) score++;
        if (strongCandle) score++;

        System.out.println("score" + score);

        // -------------------------------
        // FINAL DECISION
        // -------------------------------
        return score >= minScore
                && notTooExtended
                && notExhausted
                && rsiSafe
                && !recentSpike
                && confirmation;
    }
}