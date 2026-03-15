package com.swingStockSelector.repository;

import com.swingStockSelector.entity.StockBehavior;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface StockBehaviorRepository extends JpaRepository<StockBehavior, Long> {

    List<StockBehavior> findByTicker(String ticker);

    @Transactional
    void deleteByTickerAndStrategyName(String ticker, String strategyName);
}