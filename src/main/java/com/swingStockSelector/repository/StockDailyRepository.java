package com.swingStockSelector.repository;

import com.swingStockSelector.entity.StockPriceDaily;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StockDailyRepository extends JpaRepository<StockPriceDaily, Long> {

    List<StockPriceDaily> findByTradeDateAndScoreIsNotNullOrderByScoreDesc(LocalDate tradeDate, Pageable pageable);

    @Query("""
    SELECT s.ticker
    FROM StockPriceDaily s
    WHERE s.ticker IN :tickers
    AND s.tradeDate = :tradeDate
    """)
    List<String> findExistingTickers(
            @Param("tickers") List<String> tickers,
            @Param("tradeDate") LocalDate tradeDate);


    List<StockPriceDaily> findByTickerOrderByTradeDateAsc(String ticker);
}
