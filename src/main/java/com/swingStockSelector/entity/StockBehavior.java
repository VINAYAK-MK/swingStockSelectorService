package com.swingStockSelector.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "stock_behavior")
@Data
public class StockBehavior {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ticker;

    private String sector;

    private String strategyName;

    private double stopLossAtr;

    private double targetAtr;

    private double trailingStopAtr;

    private int maxHoldingDays;

    private double minRsi;

    private double maxRsi;

    private double minAdx;

    private double volumeMultiplier;

    private int breakoutLookback;

    private double minAtr;

    private int sampleTrades;

    // ---------------------------------
    // BREAKOUT TUNING PARAMS
    // ---------------------------------

    private Double breakoutBuffer;

    private Double minAtrMultiplier;

    private Double maxExtension;

    private Double maxCandleAtrMultiplier;

    private Double recentSpikeThreshold;

    private Double strongCloseRatio;

    // ---------------------------------
    // FEATURE FLAGS
    // ---------------------------------

    private Boolean useTrendFilter;

    private Boolean useFollowThrough;

    private Boolean useStrongClose;

    private Boolean useRsiFilter;

    private Boolean useExhaustionFilter;

    private Boolean useExtensionFilter;

    // ---------------------------------
    // DECISION PARAMS
    // ---------------------------------

    private Integer minScore;
}