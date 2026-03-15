package com.swingStockSelector.controller;

import com.swingStockSelector.api.BacktestingApi;
import com.swingStockSelector.model.BackTestRequest;
import com.swingStockSelector.model.BacktestResponse;
import com.swingStockSelector.service.SwingStockSelectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class BackTester implements BacktestingApi {

    private final SwingStockSelectorService swingStockSelectorService;

    @Override
    public ResponseEntity<BacktestResponse> runSwingPullbackBacktest(BackTestRequest backTestRequest) {
        return ResponseEntity.ok(swingStockSelectorService.backTestingEngine(backTestRequest));
    }
}
