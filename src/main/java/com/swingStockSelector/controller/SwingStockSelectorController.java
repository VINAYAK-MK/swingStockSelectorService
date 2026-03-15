package com.swingStockSelector.controller;


import com.swingStockSelector.model.ProcessTickerRequest;
import com.swingStockSelector.model.ProcessTickerResponse;
import com.swingStockSelector.service.SwingStockSelectorService;
import com.swingStockSelector.api.StockSelectionApi;
import com.swingStockSelector.model.TopLongResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class SwingStockSelectorController implements StockSelectionApi {


    private final SwingStockSelectorService swingStockSelectorService;

    @Override
    public ResponseEntity<TopLongResponse> getTopLongSelections(Integer limit, LocalDate tradeDate) {
        return ResponseEntity.ok(swingStockSelectorService.getTopLongSelections(limit, tradeDate));
    }

    @Override
    public ResponseEntity<ProcessTickerResponse> processTickers(ProcessTickerRequest processTickerRequest) {
        return ResponseEntity.ok(swingStockSelectorService.processTickers(processTickerRequest));
    }


}
