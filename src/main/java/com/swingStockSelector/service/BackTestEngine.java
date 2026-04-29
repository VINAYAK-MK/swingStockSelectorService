package com.swingStockSelector.service;

import com.swingStockSelector.config.utils.Constants;
import com.swingStockSelector.entity.StockPriceDaily;
import com.swingStockSelector.model.*;
import com.swingStockSelector.service.strategies.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackTestEngine {

    private final MeanReversionStrategy meanReversionStrategy;
    private final BreakoutStrategy breakoutStrategy;
    private final MomentumStrategy momentumStrategy;
    private final PullbackStrategy pullbackStrategy;
    private final ExitEngine exitEngine;

    private StrategyResult buildStratergyResponse(List<Trade> trades, String strategy) {

        BacktestResponse backtestResponse = new BacktestResponse();
        int wins = 0;
        int losses = 0;
        double totalProfit = 0;

        for (Trade t : trades) {

            if (t.getWin()) {
                wins++;
            } else {
                losses++;
            }

            totalProfit += t.getProfit();
        }

        double winRate = trades.isEmpty() ? 0 : (wins * 100.0) / trades.size();

        return new StrategyResult()
                .strategyName(strategy)
                .totalTrades(trades.size())
                .trades(trades)
                .wins(wins)
                .losses(losses)
                .totalProfit(totalProfit)
                .winRate(winRate);


    }

    public StrategyResult swingPullBack(String ticker, List<StockPriceDaily> candles, StrategyParams params) {

        List<Trade> trades = new ArrayList<>();

        for (int i = 0; i < candles.size(); i++) {

            StockPriceDaily yesterday = candles.get(i - 1);
            StockPriceDaily today = candles.get(i);

            if (pullbackStrategy.shouldEnter(candles, i, params)) {

                double entryPrice = today.getClose();

                Exit exit = exitEngine.calculateExit(
                        candles,
                        i,
                        params
                );
                double profit = exit.getExitPrice() - entryPrice;

                boolean win = profit > 0;

                Trade trade = new Trade(
                        ticker,
                        entryPrice,
                        exit.getExitPrice(),
                        exit.getEntryDay(),
                        exit.getExitDay(),
                        i,
                        exit.getExitIndex(),
                        Trade.ExitReasonEnum.valueOf(exit.getReason().getValue()),
                        profit,
                        win,
                        exit.getStopLoss(),
                        exit.getTarget()
                );

                trades.add(trade);

                i = exit.getExitIndex(); // skip candles until exit
            }
        }

        return buildStratergyResponse(trades, Constants.PULLBACK);
    }

    public StrategyResult momentumStrategy(String ticker, List<StockPriceDaily> candles, StrategyParams params) {

        List<Trade> trades = new ArrayList<>();

        for (int i = 50; i < candles.size(); i++) {

            StockPriceDaily today = candles.get(i);

            if (momentumStrategy.shouldEnter(candles, i, params)) {

                double entryPrice = today.getClose();
                double atr = today.getAtr14();

                Exit exit = exitEngine.calculateExit(
                        candles,
                        i,
                        params
                );

                double profit = exit.getExitPrice() - entryPrice;

                Trade trade = new Trade(
                        ticker,
                        entryPrice,
                        exit.getExitPrice(),
                        exit.getEntryDay(),
                        exit.getExitDay(),
                        i,
                        exit.getExitIndex(),
                        Trade.ExitReasonEnum.valueOf(exit.getReason().getValue()),
                        profit,
                        profit> 0,
                        exit.getStopLoss(),
                        exit.getTarget()
                );

                trades.add(trade);

                i = exit.getExitIndex();
            }
        }

        return buildStratergyResponse(trades , Constants.MOMENTUM);
    }

    public StrategyResult breakoutStrategy(String ticker, List<StockPriceDaily> candles, StrategyParams params) {

        List<Trade> trades = new ArrayList<>();

        for (int i = 20; i < candles.size(); i++) {

            StockPriceDaily today = candles.get(i);

            if (breakoutStrategy.shouldEnter(candles, i, params)) {

                double entryPrice = today.getClose();
                double atr = today.getAtr14();


                Exit exit = exitEngine.calculateExit(
                        candles,
                        i,
                        params
                );

                double profit = exit.getExitPrice() - entryPrice;

                Trade trade = new Trade(
                        ticker,
                        entryPrice,
                        exit.getExitPrice(),
                        exit.getEntryDay(),
                        exit.getExitDay(),
                        i,
                        exit.getExitIndex(),
                        Trade.ExitReasonEnum.valueOf(exit.getReason().getValue()),
                        profit,
                        profit> 0,
                        exit.getStopLoss(),
                        exit.getTarget()
                );

                trades.add(trade);

                i = exit.getExitIndex();
            }
        }

        return buildStratergyResponse(trades, Constants.BREAKOUT);
    }

    public StrategyResult meanReversionStrategy(String ticker, List<StockPriceDaily> candles, StrategyParams params) {

        List<Trade> trades = new ArrayList<>();

        for (int i = 50; i < candles.size(); i++) {

            StockPriceDaily today = candles.get(i);

            if (meanReversionStrategy.shouldEnter(candles, i, params)) {

                double entryPrice = today.getClose();
                double atr = today.getAtr14();

                Exit exit = exitEngine.calculateExit(
                        candles,
                        i,
                        params
                );

                double profit = exit.getExitPrice() - entryPrice;

                Trade trade = new Trade(
                        ticker,
                        entryPrice,
                        exit.getExitPrice(),
                        exit.getEntryDay(),
                        exit.getExitDay(),
                        i,
                        exit.getExitIndex(),
                        Trade.ExitReasonEnum.valueOf(exit.getReason().getValue()),
                        profit,
                        profit> 0,
                        exit.getStopLoss(),
                        exit.getTarget()
                );

                trades.add(trade);

                i = exit.getExitIndex();
            }
        }

        return buildStratergyResponse(trades, Constants.MEAN_REVERSION);
    }

}
