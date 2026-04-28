package com.invest.domain.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 5: Round-trip de serializacao JSON do UpdateAssetsEvent.
 *
 * For any valid UpdateAssetsEvent instance, serializing it to JSON with Jackson
 * and then deserializing the JSON back to an UpdateAssetsEvent produces an object
 * equal to the original.
 *
 * Validates: Requirements 4.5
 */
class UpdateAssetsEventSerializationProperties {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Property
    void roundTripSerializationProducesEquivalentObject(
            @ForAll("updateAssetsEvents") UpdateAssetsEvent original) throws Exception {

        String json = objectMapper.writeValueAsString(original);
        UpdateAssetsEvent deserialized = objectMapper.readValue(json, UpdateAssetsEvent.class);

        assertThat(deserialized).isEqualTo(original);
    }

    @Provide
    Arbitrary<UpdateAssetsEvent> updateAssetsEvents() {
        return Combinators.combine(
                eventTypes(),
                correlationIds(),
                eventData()
        ).as(UpdateAssetsEvent::new);
    }

    private Arbitrary<String> eventTypes() {
        return Arbitraries.of("UPDATE_ASSETS");
    }

    private Arbitrary<String> correlationIds() {
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(36).alpha().numeric();
    }

    private Arbitrary<UpdateAssetsEvent.Data> eventData() {
        return tickerLists().map(UpdateAssetsEvent.Data::new);
    }

    private Arbitrary<List<String>> tickerLists() {
        return tickers().list().ofMinSize(0).ofMaxSize(20);
    }

    private Arbitrary<String> tickers() {
        return Arbitraries.strings().ofMinLength(3).ofMaxLength(8).alpha().numeric();
    }
}
