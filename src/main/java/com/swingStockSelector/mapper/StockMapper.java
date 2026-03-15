package com.swingStockSelector.mapper;


import com.swingStockSelector.entity.StockBehavior;
import com.swingStockSelector.entity.StockPriceDaily;
import com.swingStockSelector.model.DailyIndicator;
import com.swingStockSelector.model.SelectedStock;
import com.swingStockSelector.model.StrategyParams;
import com.swingStockSelector.model.TickerIndicatorResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface StockMapper {

    SelectedStock mapToDto(StockPriceDaily stockPriceDaily);

    @Mapping(target = "ticker", source = "ticker")
    @Mapping(target = "tradeDate", source = "daily.tradeDate")

    @Mapping(target = "open", source = "daily.open")
    @Mapping(target = "high", source = "daily.high")
    @Mapping(target = "low", source = "daily.low")
    @Mapping(target = "close", source = "daily.close")
    @Mapping(target = "volume", source = "daily.volume")

    @Mapping(target = "ema20", source = "daily.ema20")
    @Mapping(target = "ema50", source = "daily.ema50")
    @Mapping(target = "ema200", source = "daily.ema200")

    @Mapping(target = "rsi14", source = "daily.rsi14")
    @Mapping(target = "rsi20", source = "daily.rsi20")

    @Mapping(target = "macd", source = "daily.macd")
    @Mapping(target = "macdSignal", source = "daily.macdSignal")
    @Mapping(target = "macdHist", source = "daily.macdHist")

    @Mapping(target = "atr14", source = "daily.atr14")

    @Mapping(target = "volumeAvg20", source = "daily.volumeAvg20")

    @Mapping(target = "highestHigh20", source = "daily.highestHigh20")
    @Mapping(target = "lowestLow20", source = "daily.lowestLow20")
    @Mapping(target = "adx14", source = "daily.adx14")

    @Mapping(target = "bbUpper", source = "daily.bbUpper")
    @Mapping(target = "bbMiddle", source = "daily.bbMiddle")
    @Mapping(target = "bbLower", source = "daily.bbLower")

    @Mapping(target = "score", source = "daily.score")

    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "id", ignore = true)
    StockPriceDaily mapToEntity(String ticker, DailyIndicator daily);


    @Mapping(source = "strategyName", target = "strategy")
    @Mapping(source = "stopLossAtr", target = "stopLossAtrMultiplier")
    @Mapping(source = "targetAtr", target = "targetAtrMultiplier")
    @Mapping(source = "maxHoldingDays", target = "maxHoldingDays")
    @Mapping(source = "minRsi", target = "minRsi")
    @Mapping(source = "maxRsi", target = "maxRsi")
    @Mapping(source = "minAdx", target = "minAdx")
    @Mapping(source = "volumeMultiplier", target = "volumeMultiplier")
    @Mapping(source = "breakoutLookback", target = "breakoutLookback")
    @Mapping(source = "minAtr", target = "minAtr")
    @Mapping(source = "trailingStopAtr", target = "trailingStopAtrMultiplier")
    @Mapping(ignore = true, target = "riskRewardRatio")
    StrategyParams mapToStrategyParams(StockBehavior stockBehavior);
}
