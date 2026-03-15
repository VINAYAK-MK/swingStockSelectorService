package com.swingStockSelector.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;


@Entity
@Table(name = "stock_price_daily",
        uniqueConstraints = @UniqueConstraint(columnNames = {"ticker", "tradeDate"}))
@Getter
@Setter
public class StockPriceDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String ticker;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

// ================= PRICE =================

    private Double open;
    private Double high;
    private Double low;
    private Double close;
    private Double volume;

// ================= TREND =================

    private Double ema20;
    private Double ema50;
    private Double ema200;

// ================= MOMENTUM =================

    private Double rsi14;
    private Double rsi20;

    private Double macd;
    private Double macdSignal;
    private Double macdHist;

// ================= VOLATILITY =================

    private Double atr14;
    private Double adx14;

// ================= VOLUME =================

    @Column(name = "volume_avg20")
    private Double volumeAvg20;

// ================= STRUCTURE =================

    private Double highestHigh20;
    private Double lowestLow20;
    private Double bbUpper;
    private Double bbMiddle;
    private Double bbLower;

// ================= STRATEGY =================

    private Double score;

// ================= AUDIT =================

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
