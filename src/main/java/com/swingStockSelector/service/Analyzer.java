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

    // 🔥 FIX 1: FIND INDEX BY DATE (CRITICAL)
    private int findIndexByDate(List<StockPriceDaily> candles, String tradeDate) {
        if (candles == null || tradeDate == null) return -1;

        for (int i = 0; i < candles.size(); i++) {
            if (tradeDate.equals(String.valueOf(candles.get(i).getTradeDate()))) {
                return i;
            }
        }
        return candles.size() - 1; // fallback
    }

    // 🔥 FIX 2: BETTER TREND LOGIC (multi-candle confirmation)
    public Trend getTrend(List<StockPriceDaily> candles, int index) {

        if (candles == null || candles.isEmpty()) return Trend.SIDEWAYS;

        int bullish = 0;
        int bearish = 0;

        for (int i = index - 2; i <= index; i++) {

            if (i < 0 || i >= candles.size()) continue;

            StockPriceDaily c = candles.get(i);
            if (c == null) continue;

            if (c.getEma20() > c.getEma50() && c.getClose() > c.getEma20())
                bullish++;

            if (c.getEma20() < c.getEma50() && c.getClose() < c.getEma20())
                bearish++;
        }

        if (bullish >= 2) return Trend.BULLISH;
        if (bearish >= 2) return Trend.BEARISH;

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

        List<StockPriceDaily> niftyCandles =
                stockDailyRepository.findByTickerOrderByTradeDateAsc("^NSEI");

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

            if (strategyResult == null || strategyResult.getTrades() == null) continue;

            String strategy = strategyResult.getStrategyName();

            Map<String, List<TradeMetrics>> grouped = new HashMap<>();

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

                metrics.setGoodEntry(tradeResult.getGoodEntry());
                metrics.setBadEntry(tradeResult.getBadEntry());
                metrics.setEntryMovePercent(tradeResult.getEntryMovePercent());

                String marketTrend = Optional.ofNullable(metrics.getMarketTrend()).orElse(Trend.SIDEWAYS.name());
                String sectorTrend = Optional.ofNullable(metrics.getSectorTrend()).orElse(Trend.SIDEWAYS.name());

                String key = strategy + "_" + marketTrend + "_" + sectorTrend;

                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(metrics);
            }

            for (Map.Entry<String, List<TradeMetrics>> entry : grouped.entrySet()) {
                String[] parts = entry.getKey().split("_");

                String strat = parts[0];
                String marketTrend = parts.length > 1 ? parts[1] : "SIDEWAYS";
                String sectorTrend = parts.length > 2 ? parts[2] : "SIDEWAYS";

                generateInsights(entry.getValue(), strat, ticker, sector, marketTrend, sectorTrend);
            }
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

        // 🔥 ENTRY QUALITY
        int lookahead = 5;
        double threshold = 0.02;

        double bestMove = 0;

        for (int i = entryIndex + 1; i <= entryIndex + lookahead && i < candles.size(); i++) {
            StockPriceDaily c = candles.get(i);
            if (c == null) continue;

            double move = (c.getHigh() - entryPrice) / entryPrice;
            if (move > bestMove) bestMove = move;
        }

        result.setGoodEntry(bestMove >= threshold ? 1 : 0);
        result.setBadEntry(bestMove < threshold ? 1 : 0);
        result.setEntryMovePercent(bestMove);

        // 🔥 FIXED TREND CALCULATION (DATE ALIGNED)
        int niftyIndex = findIndexByDate(niftyCandles, trade.getEntryDay());
        Trend marketTrend = getTrend(niftyCandles, niftyIndex);

        Trend sectorTrend = Trend.SIDEWAYS;
        if (sectorCandles != null) {
            int sectorIndex = findIndexByDate(sectorCandles, trade.getEntryDay());
            sectorTrend = getTrend(sectorCandles, sectorIndex);
        }

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

        double mfePercent = entryPrice != 0 ? (highest - entryPrice) / entryPrice : 0;
        double maePercent = entryPrice != 0 ? (entryPrice - lowest) / entryPrice : 0;

        result.setAverageMfePercent(mfePercent);
        result.setAverageMaePercent(maePercent);
        result.setAverageBarsToPeak(barsToPeak);

        return result;
    }

    private void generateInsights(List<TradeMetrics> metricsList,
                                  String strategy,
                                  String ticker,
                                  String sector,
                                  String marketTrend,
                                  String sectorTrend) {

        int total = metricsList.size();

        int goodEntries = 0;
        double totalMfe = 0;
        double totalMae = 0;
        int fastMoves = 0;

        for (TradeMetrics m : metricsList) {
            goodEntries += m.getGoodEntry();
            totalMfe += m.getMfe();
            totalMae += m.getMae();

            if (m.getBarsToPeak() <= 3) fastMoves++;
        }

        double accuracy = total > 0 ? (goodEntries * 100.0 / total) : 0;
        double avgMfe = total > 0 ? totalMfe / total : 0;
        double avgMae = total > 0 ? totalMae / total : 0;
        double fastMovePercent = total > 0 ? (fastMoves * 100.0 / total) : 0;

        log.info("==== INSIGHTS [{} | {}] ====", ticker, strategy);
        log.info("Sector: {}", sector);
        log.info("Market Trend: {}", marketTrend);
        log.info("Sector Trend: {}", sectorTrend);
        log.info("Trades: {}", total);
        log.info("Entry Accuracy: {}%", accuracy);
        log.info("Avg MFE: {}", avgMfe);
        log.info("Avg MAE: {}", avgMae);
        log.info("Fast Moves: {}%", fastMovePercent);

        if (accuracy < 50)
            log.warn("❌ Weak entries");
        else if (accuracy < 65)
            log.info("⚠️ Average entries");
        else
            log.info("✅ Strong edge");

        if (avgMae > avgMfe)
            log.warn("❌ Drawdown > profit → bad timing");

        if (fastMovePercent < 40)
            log.warn("⚠️ Slow moves → late entries");
    }
}