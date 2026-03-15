package com.swingStockSelector.service;

import com.swingStockSelector.config.utils.Constants.*;
import com.swingStockSelector.entity.StockBehavior;
import com.swingStockSelector.entity.StockPriceDaily;
import com.swingStockSelector.model.*;
import com.swingStockSelector.repository.StockBehaviorRepository;
import com.swingStockSelector.repository.StockDailyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.swingStockSelector.config.utils.Constants.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class Analyzer {

    private final StockDailyRepository stockDailyRepository;
    private final StockBehaviorRepository stockBehaviorRepository;

    public Trend getTrend(List<StockPriceDaily> candles, int index, String entryDay) {

        if (candles == null || candles.isEmpty()) return Trend.SIDEWAYS;

        int window = 3;

        int start = Math.max(0, index - window);
        int end = Math.min(candles.size() - 1, index + window);

        StockPriceDaily day = null;

        for (int i = start; i <= end; i++) {

            if (i < 0 || i >= candles.size()) continue;

            StockPriceDaily candle = candles.get(i);

            if (candle != null &&
                    entryDay != null &&
                    entryDay.equals(String.valueOf(candle.getTradeDate()))) {

                day = candle;
                break;
            }
        }

        if (day == null) {

            if (index < 0 || index >= candles.size())
                index = candles.size() - 1;

            day = candles.get(index);
        }

        if (day == null) return Trend.SIDEWAYS;

        if (day.getEma20() > day.getEma50()) return Trend.BULLISH;

        if (day.getEma20() < day.getEma50()) return Trend.BEARISH;

        return Trend.SIDEWAYS;
    }

    public void analyzer(TickerBacktestResult tickerBacktestResult,
                         List<StockPriceDaily> candles,
                         UserExpectation userExpectation) {

        if (tickerBacktestResult == null || candles == null || candles.isEmpty()) {
            log.warn("Invalid input to analyzer");
            return;
        }

        String ticker = tickerBacktestResult.getTicker();

        log.info("Analyzing ticker {}", ticker);

        List<StockPriceDaily> niftyCandles =
                stockDailyRepository.findByTickerOrderByTradeDateAsc("NIFTY");

        List<StockBehavior> behavior =
                stockBehaviorRepository.findByTicker(ticker);

        List<StockPriceDaily> sectorCandles = null;
        String sector = "UNKNOWN";

        if (behavior != null && !behavior.isEmpty()) {

            sector = behavior.get(0).getSector();

            if (sector != null) {

                sectorCandles =
                        stockDailyRepository.findByTickerOrderByTradeDateAsc(sector);
            }
        }

        for (StrategyResult strategyResult : tickerBacktestResult.getStrategyResults()) {

            if (strategyResult == null) continue;

            String strategy = strategyResult.getStrategyName();

            if (PULLBACK.equals(strategy) ||
                    MOMENTUM.equals(strategy) ||
                    MEAN_REVERSION.equals(strategy)) {

                analyzeStrategy(
                        strategyResult,
                        candles,
                        niftyCandles,
                        sectorCandles,
                        ticker,
                        sector,
                        strategy,
                        userExpectation
                );
            }
        }
    }

    private void analyzeStrategy(StrategyResult strategyResult,
                                 List<StockPriceDaily> candles,
                                 List<StockPriceDaily> niftyCandles,
                                 List<StockPriceDaily> sectorCandles,
                                 String ticker,
                                 String sector,
                                 String strategyName,
                                 UserExpectation userExpectation) {

        Map<String, List<TradeMetrics>> grouped = new HashMap<>();

        if (strategyResult.getTrades() == null) return;

        for (Trade trade : strategyResult.getTrades()) {

            if (trade == null ||
                    StringUtils.equalsAnyIgnoreCase(
                            Exit.ReasonEnum.TARGET.toString(),
                            trade.getExitReason().getValue()))
                continue;

            TradeStrategyResult tradeResult =
                    analyzeTradeLifecycle(
                            trade,
                            candles,
                            niftyCandles,
                            sectorCandles,
                            userExpectation);

            if (tradeResult == null) continue;

            TradeMetrics metrics = new TradeMetrics();

            metrics.setMfe(tradeResult.getAverageMfePercent());
            metrics.setMae(tradeResult.getAverageMaePercent());
            metrics.setBarsToPeak(tradeResult.getAverageBarsToPeak());

            metrics.setMarketTrend(tradeResult.getMarketTrend());
            metrics.setSectorTrend(tradeResult.getSectorTrend());

            metrics.setTimeExitProfitable(tradeResult.getTimeExitProfitable());
            metrics.setTimeExitSideways(tradeResult.getTimeExitSideways());
            metrics.setTimeExitLoss(tradeResult.getTimeExitLoss());

            metrics.setStoplossPremature(tradeResult.getStoplossPremature());
            metrics.setStoplossValid(tradeResult.getStoplossValid());

            String marketTrend = metrics.getMarketTrend();
            String sectorTrend = metrics.getSectorTrend();

            if (marketTrend == null) marketTrend = Trend.SIDEWAYS.name();
            if (sectorTrend == null) sectorTrend = Trend.SIDEWAYS.name();

            String key = strategyName + "_" + marketTrend + "_" + sectorTrend;

            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(metrics);
        }

        for (Map.Entry<String, List<TradeMetrics>> entry : grouped.entrySet()) {

            List<TradeMetrics> metricsList = entry.getValue();

            StrategyParams params =
                    optimizeParams(metricsList, strategyName);

            if (params == null) continue;

            saveBehavior(
                    ticker,
                    sector,
                    params,
                    metricsList.size()
            );
        }
    }

    private TradeStrategyResult analyzeTradeLifecycle(
            Trade trade,
            List<StockPriceDaily> candles,
            List<StockPriceDaily> niftyCandles,
            List<StockPriceDaily> sectorCandles,
            UserExpectation userExpectation) {

        if (trade == null || candles == null || candles.isEmpty()) return null;

        TradeStrategyResult result = new TradeStrategyResult();

        int entryIndex = trade.getEntryIndex();
        int exitIndex = trade.getExitIndex();

        if (entryIndex < 0 || entryIndex >= candles.size()) return null;

        if (exitIndex >= candles.size())
            exitIndex = candles.size() - 1;

        double entryPrice = trade.getEntryPrice();

        Trend marketTrend =
                getTrend(niftyCandles, entryIndex, trade.getEntryDay());

        Trend sectorTrend =
                sectorCandles != null
                        ? getTrend(sectorCandles, entryIndex, trade.getEntryDay())
                        : Trend.SIDEWAYS;

        result.setMarketTrend(marketTrend.name());
        result.setSectorTrend(sectorTrend.name());

        double highest = entryPrice;
        double lowest = entryPrice;
        int peakIndex = entryIndex;

        for (int i = entryIndex + 1; i <= exitIndex; i++) {

            if (i < 0 || i >= candles.size()) continue;

            StockPriceDaily candle = candles.get(i);

            if (candle == null) continue;

            if (candle.getHigh() > highest) {
                highest = candle.getHigh();
                peakIndex = i;
            }

            if (candle.getLow() < lowest) {
                lowest = candle.getLow();
            }
        }

        int barsToPeak = peakIndex - entryIndex;

        double mfePercent =
                entryPrice != 0 ? (highest - entryPrice) / entryPrice : 0;

        double maePercent =
                entryPrice != 0 ? (entryPrice - lowest) / entryPrice : 0;

        int timeExitProfitable = 0;
        int timeExitSideways = 0;
        int timeExitLoss = 0;
        int stoplossPremature = 0;
        int stoplossValid = 0;

        boolean isStopLoss =
                trade.getExitReason() != null &&
                        Exit.ReasonEnum.STOP_LOSS.getValue()
                                .equals(trade.getExitReason().getValue());

        double expectedProfit =
                userExpectation.getExpectedProfitPercent() != null
                        ? userExpectation.getExpectedProfitPercent() / 100
                        : 0.03;

        if (!isStopLoss) {

            if (mfePercent >= expectedProfit && barsToPeak <= 15)
                timeExitProfitable = 1;

            else if (mfePercent < 0.01)
                timeExitSideways = 1;

            else if (maePercent > mfePercent)
                timeExitLoss = 1;

        } else {

            if (mfePercent >= expectedProfit * 0.5)
                stoplossPremature = 1;
            else
                stoplossValid = 1;
        }

        result.setTimeExitProfitable(timeExitProfitable);
        result.setTimeExitSideways(timeExitSideways);
        result.setTimeExitLoss(timeExitLoss);

        result.setStoplossPremature(stoplossPremature);
        result.setStoplossValid(stoplossValid);

        result.setAverageMfePercent(mfePercent);
        result.setAverageMaePercent(maePercent);
        result.setAverageBarsToPeak(barsToPeak);

        return result;
    }

    private StrategyParams optimizeParams(List<TradeMetrics> metricsList,
                                          String strategy) {

        if (metricsList == null || metricsList.isEmpty()) return null;

        List<Double> mfeList = new ArrayList<>();
        List<Double> maeList = new ArrayList<>();
        List<Integer> barsList = new ArrayList<>();

        for (TradeMetrics m : metricsList) {

            mfeList.add(m.getMfe());
            maeList.add(m.getMae());
            barsList.add(m.getBarsToPeak());
        }

        Collections.sort(mfeList);
        Collections.sort(maeList);
        Collections.sort(barsList);

        double mfe70 = percentile(mfeList, 0.7);
        double mae70 = percentile(maeList, 0.7);
        int bars60 = barsList.get((int) (barsList.size() * 0.6));

        double stopLoss = Math.max(mae70 * 1.2, 0.005);

        double target = Math.max(mfe70 * 1.1, stopLoss * 2);

        int maxHolding = bars60 + 2;

        StrategyParams params = new StrategyParams();

        params.setStrategy(strategy);

        params.setStopLossAtrMultiplier(stopLoss);
        params.setTargetAtrMultiplier(target);
        params.setTrailingStopAtrMultiplier(stopLoss * 0.75);

        params.setMaxHoldingDays(maxHolding);

        params.setMinRsi(40.0);
        params.setMaxRsi(70.0);
        params.setMinAdx(20.0);

        params.setVolumeMultiplier(1.5);
        params.setMinAtr(0.5);

        params.setRiskRewardRatio(target / stopLoss);

        log.info("Optimized Params -> {}", params);

        return params;
    }

    private double percentile(List<Double> list, double p) {

        int index = (int) Math.ceil(p * list.size()) - 1;

        index = Math.max(0, Math.min(index, list.size() - 1));

        return list.get(index);
    }

    private void saveBehavior(String ticker,
                              String sector,
                              StrategyParams params,
                              int sampleTrades) {

        try {

            StockBehavior behavior = new StockBehavior();

            behavior.setTicker(ticker);
            behavior.setSector(sector);

            behavior.setStrategyName(params.getStrategy());

            behavior.setStopLossAtr(params.getStopLossAtrMultiplier());
            behavior.setTargetAtr(params.getTargetAtrMultiplier());
            behavior.setTrailingStopAtr(params.getTrailingStopAtrMultiplier());

            behavior.setMaxHoldingDays(params.getMaxHoldingDays());

            behavior.setMinRsi(params.getMinRsi());
            behavior.setMaxRsi(params.getMaxRsi());
            behavior.setMinAdx(params.getMinAdx());

            behavior.setVolumeMultiplier(params.getVolumeMultiplier());
            behavior.setMinAtr(params.getMinAtr());

            behavior.setSampleTrades(sampleTrades);

            stockBehaviorRepository.deleteByTickerAndStrategyName(
                    ticker,
                    behavior.getStrategyName());

            stockBehaviorRepository.save(behavior);

        } catch (Exception e) {

            log.error("Failed saving behavior for ticker {}", ticker, e);
        }
    }
}