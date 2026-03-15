package com.swingStockSelector.service.strategies;

import com.swingStockSelector.entity.StockPriceDaily;
import com.swingStockSelector.model.Exit;
import com.swingStockSelector.model.StrategyParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExitEngine {

    public Exit calculateExit(
            List<StockPriceDaily> candles,
            int entryIndex,
            StrategyParams params
    ) {

        double entryPrice = candles.get(entryIndex).getClose();
        int holdingDays = 0;

        StockPriceDaily entryDayCandle = candles.get(entryIndex);
        double atr = entryDayCandle.getAtr14();

        double stopLoss = entryPrice - params.getStopLossAtrMultiplier() * atr;
        double target = entryPrice + params.getTargetAtrMultiplier() * atr;

        String entryDay = entryDayCandle.getTradeDate().toString();

        for (int i = entryIndex + 1; i < candles.size(); i++) {

            StockPriceDaily day = candles.get(i);
            holdingDays++;

            String exitDay = day.getTradeDate().toString();

            // Stop loss
            if (day.getLow() <= stopLoss) {
                return new Exit(
                        exitDay,
                        entryDay,
                        stopLoss,
                        Exit.ReasonEnum.STOP_LOSS,
                        i,
                        stopLoss,
                        target
                );
            }

            // Target
            if (day.getHigh() >= target) {
                return new Exit(
                        exitDay,
                        entryDay,
                        target,
                        Exit.ReasonEnum.TARGET,
                        i,
                        stopLoss,
                        target
                );
            }

            // Time exit
            if (holdingDays >= params.getMaxHoldingDays()) {
                return new Exit(
                        exitDay,
                        entryDay,
                        day.getClose(),
                        Exit.ReasonEnum.TIME_EXIT,
                        i,
                        stopLoss,
                        target
                );
            }
        }

        StockPriceDaily lastDay = candles.get(candles.size() - 1);

        return new Exit(
                lastDay.getTradeDate().toString(),
                entryDay,
                lastDay.getClose(),
                Exit.ReasonEnum.DATA_END,
                candles.size() - 1,
                stopLoss,
                target
        );
    }
}