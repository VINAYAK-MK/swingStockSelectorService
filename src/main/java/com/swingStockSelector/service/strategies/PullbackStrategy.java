package com.swingStockSelector.service.strategies;

import com.swingStockSelector.entity.StockPriceDaily;
import com.swingStockSelector.model.StrategyParams;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PullbackStrategy implements TradingStrategy {

    @Override
    public boolean shouldEnter(List<StockPriceDaily> candles, int i, StrategyParams params) {

        StockPriceDaily today = candles.get(i);
        StockPriceDaily yesterday = candles.get(i - 1);

        boolean trend =
                today.getClose() > today.getEma200() &&
                today.getEma20() > today.getEma50() &&
                today.getEma50() > today.getEma200();

        boolean pullback =
                today.getRsi14() >= params.getMinRsi() &&
                today.getRsi14() <= params.getMaxRsi() &&
                today.getLow() <= today.getEma20();

        boolean trigger =
                today.getClose() > yesterday.getHigh();

        boolean volume =
                today.getVolume() > today.getVolumeAvg20() * params.getVolumeMultiplier();

        return trend && pullback && trigger && volume;
    }
}