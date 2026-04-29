package com.swingStockSelector.service;

import com.swingStockSelector.config.PythonClientConfig;
import com.swingStockSelector.model.SelectedStock;
import com.swingStockSelector.model.TickerIndicatorResponse;
import com.swingStockSelector.model.YahooRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class PythonClient {

    private final PythonClientConfig config;

    public Map<String, List<Map<String, Object>>> getYahooData(YahooRequest request) {

        RestTemplate restTemplate = config.restTemplate();
        String url = config.getBaseUrl() + "/yahoo-data";

        return restTemplate.postForObject(
                url,
                request,
                Map.class
        );
    }

    @Async("batchExecutor")
    public CompletableFuture<List<TickerIndicatorResponse>> calculateIndicators(List<String> tickers) {

        RestTemplate restTemplate = config.restTemplate();
        String url =  config.getBaseUrl() + "/calculate";
        System.out.println(url);

        TickerIndicatorResponse[] response =
                restTemplate.postForObject(
                        url,
                        tickers,
                        TickerIndicatorResponse[].class
                );
        List<TickerIndicatorResponse> responseList =
                Optional.ofNullable(response)
                        .map(Arrays::asList)
                        .orElseGet(Collections::emptyList);

        return CompletableFuture.completedFuture(responseList);
    }
}