package com.swingStockSelector.service;

import com.swingStockSelector.config.utils.Constants;
import com.swingStockSelector.entity.StockBehavior;
import com.swingStockSelector.entity.StockPriceDaily;
import com.swingStockSelector.mapper.StockMapper;
import com.swingStockSelector.model.*;
import com.swingStockSelector.repository.StockBehaviorRepository;
import com.swingStockSelector.repository.StockDailyRepository;
import com.swingStockSelector.service.strategies.BreakoutStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.SwingWrapper;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.swingStockSelector.config.utils.Constants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwingStockSelectorService {

    private final StockDailyRepository stockDailyRepository;
    private final StockMapper mapper;
    private final PythonClient pyClient;
    private final BackTestEngine strategy;
    private final BreakoutStrategy breakoutStrategy;
    private final Analyzer analyzer;
    private final StockBehaviorRepository stockBehaviorRepository;

    public TopLongResponse getTopLongSelections(Integer limit, LocalDate tradeDate) {
        try {

            if (limit == null || limit <= 0) {
                limit = 5;
            }

            if (tradeDate == null) {
                tradeDate = LocalDate.now();
            }

            Pageable pageable = PageRequest.of(0, limit);

            List<StockPriceDaily> stocks =
                    stockDailyRepository
                            .findByTradeDateAndScoreIsNotNullOrderByScoreDesc(
                                    tradeDate,
                                    pageable
                            );

            List<SelectedStock> selectedStocks = stocks.stream()
                    .map(mapper::mapToDto)
                    .collect(Collectors.toList());

            TopLongResponse response = new TopLongResponse();
            response.setTradeDate(String.valueOf(tradeDate));
            response.setCount(selectedStocks.size());
            response.setStocks(selectedStocks);

            return response;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ProcessTickerResponse processTickers(ProcessTickerRequest processTickerRequest) {
        try{
            if(Objects.isNull(processTickerRequest) || CollectionUtils.isEmpty(processTickerRequest.getTickers()))
            {
                return new ProcessTickerResponse().message("Request doesn't contain required parameters");
            }

            List<String> tickersExistingInDB = stockDailyRepository.findExistingTickers(processTickerRequest.getTickers(), LocalDate.now());
            Set<String> existingSet = new HashSet<>(tickersExistingInDB);
            Integer BATCH_SIZE = 5;
            List<String> missingTickers = processTickerRequest.getTickers().stream()
                    .filter(ticker -> !existingSet.contains(ticker))
                    .toList();

            List<List<String>> batchedTickers = createBatch(missingTickers, BATCH_SIZE);

           //  Trigger async calls
            List<CompletableFuture<List<TickerIndicatorResponse>>> futures =
                    batchedTickers.stream()
                            .map(pyClient::calculateIndicators)
                            .toList();

            //  Wait for all to complete
            CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            ).join();

            //  Merge all results
            List<TickerIndicatorResponse> listOfStocks =
                    futures.stream()
                            .flatMap(future -> future.join().stream())
                            .toList();

           //  Convert to entities
            List<StockPriceDaily> listOfEntities =
                    listOfStocks.stream()
                            .flatMap(stock ->
                                    stock.getData().stream()
                                            .map(daily ->
                                                    mapper.mapToEntity(stock.getTicker(), daily)
                                            )
                            )
                            .toList();


            //  Save to DB
            stockDailyRepository.saveAll(listOfEntities);

        }catch (Exception e){
            throw new RuntimeException(e);
        }
        return new ProcessTickerResponse().processedCount(processTickerRequest.getTickers().size()).message("Added new information to DB");

    }

    private List<List<String>> createBatch(List<String> tickers, Integer batchSize)
    {
        if (tickers == null || tickers.isEmpty()) {
            return Collections.emptyList();
        }

        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be greater than 0");
        }

        List<List<String>> batches = new ArrayList<>();

        for (int i = 0; i < tickers.size(); i += batchSize) {
            int end = Math.min(i + batchSize, tickers.size());
            batches.add(tickers.subList(i, end));
        }

        return batches;
    }

    public BacktestResponse backTestingEngine(BackTestRequest backTestRequest){

        BacktestResponse backTestResponseFinal = new BacktestResponse();
        List<String> tickers = backTestRequest.getTickers();
        List<String> strategies = backTestRequest.getStrategies();


        if (tickers == null || tickers.isEmpty()) {
            return null;
        }

        for (String ticker : tickers) {

            TickerBacktestResult tickerBacktestResult = new TickerBacktestResult().ticker(ticker);

            // Fetch historical data
            List<StockPriceDaily> candles =
                    stockDailyRepository.findByTickerOrderByTradeDateAsc(ticker);

            int totalSize = candles.size();

            int splitIndex = (int) (totalSize * 0.6);

            List<StockPriceDaily> trainingData = candles.subList(0, totalSize-1);

            List<StockPriceDaily> testingData = candles.subList(splitIndex, totalSize);

            List<StockBehavior> stockBehaviors = stockBehaviorRepository.findByTicker(ticker);

            Map<String, StrategyParams> paramsMap =
                    stockBehaviors.stream()
                            .map(mapper::mapToStrategyParams)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toMap(
                                    StrategyParams::getStrategy,
                                    p -> p,
                                    (existing, replacement) -> existing // avoid duplicate crash
                            ));

            List<String> allStrategies = Arrays.asList(
                    PULLBACK,
                    MOMENTUM,
                    BREAKOUT,
                    MEAN_REVERSION
            );

            for (String strategy : allStrategies) {
                paramsMap.computeIfAbsent(strategy, this::getDefaultParams);
            }

            if (candles == null || candles.isEmpty()) {
                continue;
            }

            // Run selected strategies
            if (strategies.isEmpty() || strategies.contains(Constants.PULLBACK)) {


                StrategyResult strategyResult = strategy.swingPullBack(ticker, trainingData, paramsMap.get(Constants.PULLBACK));

                tickerBacktestResult.addStrategyResultsItem(strategyResult);

            }
            // Run selected strategies
            if (strategies.isEmpty() || strategies.contains(MEAN_REVERSION)) {

                StrategyResult strategyResult = strategy.meanReversionStrategy(ticker, trainingData, paramsMap.get(MEAN_REVERSION));

                tickerBacktestResult.addStrategyResultsItem(strategyResult);

            }
            // Run selected strategies
            if (strategies.isEmpty() || strategies.contains(MOMENTUM)) {

                StrategyResult strategyResult = strategy.momentumStrategy(ticker, trainingData, paramsMap.get(MOMENTUM));

                tickerBacktestResult.addStrategyResultsItem(strategyResult);

            }
            // Run selected strategies
            if (strategies.isEmpty() || strategies.contains(BREAKOUT)) {

                StrategyResult strategyResult = strategy.breakoutStrategy(ticker, trainingData, paramsMap.get(BREAKOUT));

                tickerBacktestResult.addStrategyResultsItem(strategyResult);
                for (Trade trade : strategyResult.getTrades()) {

                    plotTradeAndSave(
                            ticker,
                            candles,
                            trade
                    );

                }

            }
            backTestResponseFinal.addTickersItem(tickerBacktestResult);

            analyzer.analyzer(tickerBacktestResult, candles, backTestRequest.getUserExpectation());

        }

        return backTestResponseFinal;
    }

    private StrategyParams getDefaultParams(String strategy) {

        StrategyParams params = new StrategyParams();

        params.setStrategy(strategy);

        // Risk management
        params.setStopLossAtrMultiplier(1.2);
        params.setTargetAtrMultiplier(2.5);
        params.setTrailingStopAtrMultiplier(1.0);
        params.setRiskRewardRatio(2.0);

        // Holding
        params.setMaxHoldingDays(10);

        // Indicators
        params.setMinRsi(50.0);
        params.setMaxRsi(70.0);
        params.setMinAdx(25.0);

        // Breakout
        params.setVolumeMultiplier(2.0);
        params.setBreakoutLookback(20);

        // Volatility filter
        params.setMinAtr(1.0);

        return params;
    }

    private void plotTradeAndSave(String ticker,
                                  List<StockPriceDaily> candles,
                                  Trade trade) {

        try {
            LocalDate startDate = LocalDate.parse(trade.getEntryDay());
            LocalDate endDate   = LocalDate.parse(trade.getExitDay());

            List<Date> xData = new ArrayList<>();

            List<Double> closeData = new ArrayList<>();
            List<Double> highData  = new ArrayList<>();
            List<Double> lowData   = new ArrayList<>();
            List<Double> openData = new ArrayList<>();

            for (StockPriceDaily candle : candles) {
                LocalDate date = candle.getTradeDate();

                if ((date.isEqual(startDate) || date.isAfter(startDate)) &&
                        (date.isEqual(endDate) || date.isBefore(endDate))) {

                    xData.add(java.sql.Date.valueOf(date));

                    closeData.add(candle.getClose());
                    highData.add(candle.getHigh());
                    lowData.add(candle.getLow());
                    openData.add(candle.getOpen());
                }
            }

            if (xData.isEmpty()) return;

            XYChart chart = new XYChartBuilder()
                    .width(900)
                    .height(500)
                    .title(ticker + " Trade (" + startDate + " → " + endDate + ")")
                    .xAxisTitle("Date")
                    .yAxisTitle("Price")
                    .build();

            // Main price lines
            chart.addSeries("Close", xData, closeData);
            chart.addSeries("High", xData, highData);
            chart.addSeries("Low", xData, lowData);
            chart.addSeries("Open", xData, openData);

            // Entry marker
            LocalDate actualEntryDate = null;

            for (StockPriceDaily candle : candles) {
                if (candle.getTradeDate().isAfter(startDate)) {
                    actualEntryDate = candle.getTradeDate();
                    break;
                }
            }
            if (actualEntryDate != null) {
                chart.addSeries("Entry",
                        List.of(java.sql.Date.valueOf(actualEntryDate)),
                        List.of(trade.getEntryPrice()));
            }

            // Exit marker
            chart.addSeries("Exit",
                    List.of(java.sql.Date.valueOf(endDate)),
                    List.of(trade.getExitPrice()));

            // Optional: Stop loss & target lines
            chart.addSeries("StopLoss", xData,
                    Collections.nCopies(xData.size(), trade.getStopLoss()));

            chart.addSeries("Target", xData,
                    Collections.nCopies(xData.size(), trade.getTarget()));

            // Save chart
            String dir = "charts/";
            new File(dir).mkdirs();

            String fileName = dir + ticker + "_" +
                    startDate + "_" + endDate +
                    "_" + trade.getExitReason();

            BitmapEncoder.saveBitmap(chart, fileName, BitmapEncoder.BitmapFormat.PNG);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ShouldEnterResponse getShouldEnter(ShouldEnterRequest request) {

        List<StockEntryResult> results = new ArrayList<>();

        StrategyParams params = new StrategyParams();
        params.setBreakoutLookback(request.getBreakoutLookback());
        params.setMinAtr(request.getMinAtr());

        // 🔥 Get ALL distinct tickers from DB
        List<String> tickers = stockDailyRepository.findAllDistinctTickers();
        System.out.println(tickers);

        for (String ticker : tickers) {

            List<StockPriceDaily> candles =
                    stockDailyRepository.findByTickerOrderByTradeDateAsc(ticker);

            if (candles == null || candles.size() <= request.getBreakoutLookback()) {

                StockEntryResult result = new StockEntryResult();
                result.setTicker(ticker);
                result.setShouldEnter(false);
                result.setReason("Not enough data");

                results.add(result);
                continue;
            }

            int lastIndex = candles.size() - 1;
            StockPriceDaily today = candles.get(lastIndex);

            boolean shouldEnter =
                    breakoutStrategy.shouldEnter(candles, lastIndex, params);

            double highestHigh = candles.subList(
                            lastIndex - request.getBreakoutLookback(),
                            lastIndex
                    ).stream()
                    .mapToDouble(StockPriceDaily::getHigh)
                    .max()
                    .orElse(0);

            StockEntryResult result = new StockEntryResult();
            result.setTicker(ticker);
            result.setShouldEnter(shouldEnter);
            result.setClose(today.getClose());
            result.setHighestHigh(highestHigh);
            result.setReason(shouldEnter ? "Breakout Valid" : "Conditions Not Met");

            results.add(result);
        }

        ShouldEnterResponse response = new ShouldEnterResponse();
        response.setResults(results);
        return response;
    }
}