package com.invest.adapters.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface JpaAssetRepository extends JpaRepository<AssetEntity, Long> {

    @Query("SELECT DISTINCT a FROM AssetEntity a LEFT JOIN FETCH a.indicatorValues WHERE a.ticker IN :tickers")
    List<AssetEntity> findByTickerIn(@Param("tickers") Collection<String> tickers);
}
