package com.swingStockSelector.service.strategies;

import com.swingStockSelector.entity.StockPriceDaily;
import com.swingStockSelector.model.StrategyParams;

import java.util.List;

public interface TradingStrategy {

    boolean shouldEnter(
            List<StockPriceDaily> candles,
            int index,
            StrategyParams params
    );
}