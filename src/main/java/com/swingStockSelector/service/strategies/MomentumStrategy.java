package com.swingStockSelector.service.strategies;

import com.swingStockSelector.entity.StockPriceDaily;
import com.swingStockSelector.model.StrategyParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MomentumStrategy implements TradingStrategy {

    @Override
    public boolean shouldEnter(List<StockPriceDaily> candles, int i, StrategyParams params) {

        StockPriceDaily today = candles.get(i);

        boolean strongTrend =
                today.getAdx14() >= params.getMinAdx();

        boolean momentum =
                today.getRsi14() > params.getMaxRsi();

        boolean aboveEma =
                today.getClose() > today.getEma20();

        boolean volume =
                today.getVolume() > today.getVolumeAvg20() * params.getVolumeMultiplier();

        return strongTrend && momentum && aboveEma && volume;
    }
}