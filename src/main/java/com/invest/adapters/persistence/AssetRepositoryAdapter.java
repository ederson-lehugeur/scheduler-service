package com.invest.adapters.persistence;

import com.invest.domain.entities.Asset;
import com.invest.domain.ports.out.AssetRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public class AssetRepositoryAdapter implements AssetRepository {

    private final JpaAssetRepository jpaRepository;

    public AssetRepositoryAdapter(JpaAssetRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<Asset> findByTickers(Set<String> tickers) {
        return jpaRepository.findByTickerIn(tickers).stream()
                .map(AssetMapper::toDomain)
                .toList();
    }
}
