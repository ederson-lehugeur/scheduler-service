package com.invest.adapters.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface JpaAssetRepository extends JpaRepository<AssetEntity, Long> {

    List<AssetEntity> findByTickerIn(Collection<String> tickers);
}
