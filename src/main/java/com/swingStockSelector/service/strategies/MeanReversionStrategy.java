package com.swingStockSelector.service.strategies;

import com.swingStockSelector.entity.StockPriceDaily;
import com.swingStockSelector.model.StrategyParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MeanReversionStrategy implements TradingStrategy {

    @Override
    public boolean shouldEnter(List<StockPriceDaily> candles, int i, StrategyParams params) {

        StockPriceDaily today = candles.get(i);

        boolean sideways =
                today.getAdx14() < params.getMinAdx();

        boolean oversold =
                today.getRsi14() < params.getMinRsi();

        boolean lowerBandTouch =
                today.getLow() <= today.getBbLower();

        boolean reversal =
                today.getClose() > today.getOpen();

        return sideways && oversold && lowerBandTouch && reversal;
    }
}