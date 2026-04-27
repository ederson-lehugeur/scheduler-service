package com.invest.domain.ports.out;

import com.invest.domain.entities.Asset;

import java.util.List;
import java.util.Set;

public interface AssetRepository {

    List<Asset> findByTickers(Set<String> tickers);
}
