package com.swingStockSelector.service;

import com.swingStockSelector.config.PythonClientConfig;
import com.swingStockSelector.model.SelectedStock;
import com.swingStockSelector.model.TickerIndicatorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class PythonClient {

    private final PythonClientConfig config;

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