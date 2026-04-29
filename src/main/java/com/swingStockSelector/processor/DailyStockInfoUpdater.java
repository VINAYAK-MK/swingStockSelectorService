package com.swingStockSelector.processor;

import com.swingStockSelector.entity.StockPriceDaily;
import com.swingStockSelector.model.YahooRequest;
import com.swingStockSelector.repository.StockDailyRepository;
import com.swingStockSelector.service.IndicatorService;
import com.swingStockSelector.service.PythonClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyStockInfoUpdater implements ApplicationRunner {

    private final StockDailyRepository stockDailyRepository;
    private final PythonClient pythonClient; // Feign/RestTemplate client
    private final IndicatorService indicatorService;

    @Override
    public void run(ApplicationArguments args) {

        log.info("Starting Daily Stock Info Updater...");

        List<String> tickers = stockDailyRepository.findDistinctTickers();

        for (String ticker : tickers) {
            try {
                processTicker(ticker);
            } catch (Exception e) {
                log.error("Error processing ticker {}: {}", ticker, e.getMessage(), e);
            }
        }

        log.info("Daily Stock Info Updater completed.");
    }

    private void processTicker(String ticker) {

        // 1. Get last stored date
        LocalDate lastDate = stockDailyRepository.findLastDateByTicker(ticker);

        if (lastDate == null) {
            log.warn("No data found for ticker {}. Skipping...", ticker);
            return;
        }

        LocalDate startDate = lastDate.plusDays(1);
        LocalDate endDate = LocalDate.now();

        log.info("Data missing for {} from {} to {}", ticker,startDate,endDate);

        if (startDate.isAfter(endDate)) {
            log.info("No new data for ticker {}", ticker);
            return;
        }

        // 2. Call Python API
        Map<String, List<Map<String, Object>>> response =
                pythonClient.getYahooData(
                        new YahooRequest().tickers(List.of(ticker)).startDate(startDate).endDate(endDate)
                );

        List<Map<String, Object>> newData = response.get(ticker);

        if (newData == null || newData.isEmpty()) {
            log.info("No new data from API for ticker {}", ticker);
            return;
        }

        // 3. Get previous stored row (IMPORTANT)
        StockPriceDaily prevDayData =
                stockDailyRepository.findTopByTickerOrderByTradeDateDesc(ticker);

        // 4. Process each new day
        for (Map<String, Object> dayData : newData) {

            StockPriceDaily newRecord =
                    indicatorService.calculateFromPrevious(prevDayData, dayData);

            stockDailyRepository.save(newRecord);

            prevDayData = newRecord; // update for next iteration
        }

        log.info("Updated ticker {} with {} new records", ticker, newData.size());
    }
}