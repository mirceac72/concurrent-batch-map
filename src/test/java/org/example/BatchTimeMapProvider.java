package org.example;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

import java.math.BigDecimal;
import java.util.stream.Stream;

public class BatchTimeMapProvider implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
        return Stream.of(Arguments.of(new BlockingBatchMap<String, TimestampedValue<BigDecimal>>() {
                    @Override
                    public TimestampedValue<BigDecimal> mergeFn(TimestampedValue<BigDecimal> existingValue, TimestampedValue<BigDecimal> newValue) {
                        return TimestampedValue.latest(existingValue, newValue);
                    }
                }),
                Arguments.of(new LowBlockingBatchMap<String, TimestampedValue<BigDecimal>>() {
                    @Override
                    public TimestampedValue<BigDecimal> mergeFn(TimestampedValue<BigDecimal> existingValue, TimestampedValue<BigDecimal> newValue) {
                        return TimestampedValue.latest(existingValue, newValue);
                    }
                }));
    }
}
