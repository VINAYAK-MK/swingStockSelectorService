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

        StockPriceDaily today = candles.get(i);

        int lookback = params.getBreakoutLookback();

        double highestHigh = Double.MIN_VALUE;

        for (int j = i - lookback; j < i; j++) {
            highestHigh = Math.max(highestHigh, candles.get(j).getHigh());
        }

        boolean breakout =
                today.getClose() > highestHigh;

        boolean volume =
                today.getVolume() > today.getVolumeAvg20() * params.getVolumeMultiplier();

        boolean volatility =
                today.getAtr14() >= params.getMinAtr();

        return breakout && volume && volatility;
    }
}