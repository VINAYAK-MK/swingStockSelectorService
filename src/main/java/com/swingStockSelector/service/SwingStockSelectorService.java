package com.swingStockSelector.service;

import com.swingStockSelector.config.utils.Constants;
import com.swingStockSelector.entity.StockBehavior;
import com.swingStockSelector.entity.StockPriceDaily;
import com.swingStockSelector.mapper.StockMapper;
import com.swingStockSelector.model.*;
import com.swingStockSelector.repository.StockBehaviorRepository;
import com.swingStockSelector.repository.StockDailyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwingStockSelectorService {

    private final StockDailyRepository stockDailyRepository;
    private final StockMapper mapper;
    private final PythonClient pyClient;
    private final BackTestEngine strategy;
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
            List<StockPriceDaily> candles = stockDailyRepository.findByTickerOrderByTradeDateAsc(ticker);

            List<StockBehavior> stockBehaviors = stockBehaviorRepository.findByTicker(ticker);

            Map<String, StrategyParams> paramsMap =
                    stockBehaviors.stream()
                            .map(mapper::mapToStrategyParams)
                            .collect(Collectors.toMap(
                                    StrategyParams::getStrategy,
                                    p -> p
                            ));

            if (candles == null || candles.isEmpty()) {
                continue;
            }

            // Run selected strategies
            if (strategies.isEmpty() || strategies.contains(Constants.PULLBACK)) {


                StrategyResult strategyResult = strategy.swingPullBack(ticker, candles, paramsMap.get(Constants.PULLBACK));

                tickerBacktestResult.addStrategyResultsItem(strategyResult);

            }
            // Run selected strategies
            if (strategies.isEmpty() || strategies.contains(Constants.MEAN_REVERSION)) {

                StrategyResult strategyResult = strategy.meanReversionStrategy(ticker, candles, paramsMap.get(Constants.MEAN_REVERSION));

                tickerBacktestResult.addStrategyResultsItem(strategyResult);

            }
            // Run selected strategies
            if (strategies.isEmpty() || strategies.contains(Constants.MOMENTUM)) {

                StrategyResult strategyResult = strategy.momentumStrategy(ticker, candles, paramsMap.get(Constants.MOMENTUM));

                tickerBacktestResult.addStrategyResultsItem(strategyResult);

            }
            // Run selected strategies
            if (strategies.isEmpty() || strategies.contains(Constants.BREAKOUT)) {

                StrategyResult strategyResult = strategy.breakoutStrategy(ticker, candles, paramsMap.get(Constants.BREAKOUT));

                tickerBacktestResult.addStrategyResultsItem(strategyResult);

            }
            backTestResponseFinal.addTickersItem(tickerBacktestResult);

        }

        return backTestResponseFinal;
    }
}