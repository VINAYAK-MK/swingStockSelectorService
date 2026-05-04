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


    @Mapping(target = "strategy", source = "strategyName")

    // Risk
    @Mapping(target = "stopLossAtrMultiplier", source = "stopLossAtr")
    @Mapping(target = "targetAtrMultiplier", source = "targetAtr")
    @Mapping(target = "trailingStopAtrMultiplier", source = "trailingStopAtr")

    // Core filters
    @Mapping(target = "maxHoldingDays", source = "maxHoldingDays")
    @Mapping(target = "minRsi", source = "minRsi")
    @Mapping(target = "maxRsi", source = "maxRsi")
    @Mapping(target = "minAdx", source = "minAdx")
    @Mapping(target = "volumeMultiplier", source = "volumeMultiplier")
    @Mapping(target = "breakoutLookback", source = "breakoutLookback")
    @Mapping(target = "minAtr", source = "minAtr")

    // Breakout tuning
    @Mapping(target = "breakoutBuffer", source = "breakoutBuffer")
    @Mapping(target = "minAtrMultiplier", source = "minAtrMultiplier")
    @Mapping(target = "maxExtension", source = "maxExtension")
    @Mapping(target = "maxCandleAtrMultiplier", source = "maxCandleAtrMultiplier")
    @Mapping(target = "recentSpikeThreshold", source = "recentSpikeThreshold")
    @Mapping(target = "strongCloseRatio", source = "strongCloseRatio")

    // Feature flags
    @Mapping(target = "useTrendFilter", source = "useTrendFilter")
    @Mapping(target = "useFollowThrough", source = "useFollowThrough")
    @Mapping(target = "useStrongClose", source = "useStrongClose")
    @Mapping(target = "useRsiFilter", source = "useRsiFilter")
    @Mapping(target = "useExhaustionFilter", source = "useExhaustionFilter")
    @Mapping(target = "useExtensionFilter", source = "useExtensionFilter")

    // Decision
    @Mapping(target = "minScore", source = "minScore")
    StrategyParams mapToStrategyParams(StockBehavior stockBehavior);
}
