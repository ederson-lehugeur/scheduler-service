package com.invest.adapters.persistence;

import com.invest.domain.entities.Asset;
import com.invest.domain.entities.IndicatorValue;
import com.invest.domain.entities.enumerator.IndicatorType;

import java.util.List;

public final class AssetMapper {

    private AssetMapper() {}

    public static Asset toDomain(AssetEntity entity) {
        List<IndicatorValue> indicatorValues = entity.getIndicatorValues().stream()
                .map(iv -> new IndicatorValue(
                        IndicatorType.fromCode(iv.getId().getIndicatorType()).orElseThrow(),
                        iv.getValue()
                ))
                .toList();

        return Asset.builder()
                .id(entity.getId())
                .ticker(entity.getTicker())
                .name(entity.getName())
                .assetType(entity.getAssetType())
                .indicatorValues(indicatorValues)
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
