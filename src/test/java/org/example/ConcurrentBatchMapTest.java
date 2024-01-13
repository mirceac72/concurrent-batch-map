package org.example;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.example.KeyValue.kv;
import static org.example.TimestampedValue.tv;
import static org.junit.jupiter.api.Assertions.*;

class ConcurrentBatchMapTest {

    @ParameterizedTest
    @ArgumentsSource(BatchTimeMapProvider.class)
    public void provideValueAfterComplete(ConcurrentBatchMap<String, TimestampedValue<BigDecimal>> map) {

        var key = "X12";
        var timestamp = OffsetDateTime.now();
        var value = BigDecimal.valueOf(15);

        List<KeyValue<String, TimestampedValue<BigDecimal>>> kTVs =
                List.of(kv(key, tv(timestamp, value)));

        map.beginBatch();
        map.putAll(kTVs);
        map.completeBatch();

        var expectedTimestampedValue = kTVs.getFirst().value();
        var actualTimestampedValue = map.get(key);

        assertEquals(expectedTimestampedValue, actualTimestampedValue);
    }

    @ParameterizedTest
    @ArgumentsSource(BatchTimeMapProvider.class)
    public void provideLatestValueAfterComplete(ConcurrentBatchMap<String, TimestampedValue<BigDecimal>> map) {

        var key1 = "X12";
        var timestamp1 = OffsetDateTime.now();
        var value1 = BigDecimal.valueOf(15);

        var key2 = "X24";
        var timestamp2 = OffsetDateTime.now();
        var value2 = BigDecimal.valueOf(25);

        var newerTimestamp1 = timestamp1.plusMinutes(1);
        var newerValue1 = BigDecimal.valueOf(17);

        var newerTimestamp2 = timestamp2.minusMinutes(1);
        var newerValue2 = BigDecimal.valueOf(27);

        List<KeyValue<String, TimestampedValue<BigDecimal>>> kTVs =
                List.of(kv(key1, tv(timestamp1, value1)), kv(key2, tv(timestamp2, value2)));

        map.beginBatch();
        map.putAll(kTVs);
        map.completeBatch();

        List<KeyValue<String, TimestampedValue<BigDecimal>>> newerKTVs =
                List.of(kv(key1, tv(newerTimestamp1, newerValue1)), kv(key2, tv(newerTimestamp2, newerValue2)));

        map.beginBatch();
        map.putAll(newerKTVs);
        map.completeBatch();

        var expectedTimestampedValue1 = newerKTVs.get(0).value();
        var actualTimestampedValue1 = map.get(key1);

        var expectedTimestampedValue2 = kTVs.get(1).value();
        var actualTimestampedValue2 = map.get(key2);

        assertEquals(expectedTimestampedValue1, actualTimestampedValue1);
        assertEquals(expectedTimestampedValue2, actualTimestampedValue2);
    }

    @ParameterizedTest
    @ArgumentsSource(BatchTimeMapProvider.class)
    public void provideLatestValueConcurrentScenario(ConcurrentBatchMap<String, TimestampedValue<BigDecimal>> map) {

        String keyPrefix = "X";
        OffsetDateTime startTime = OffsetDateTime.now();
        BigDecimal startValue = BigDecimal.valueOf(1);
        int concurrency = 10;
        int iterations = 10;
        int batchSize = 10;

        for (int counter = 0; counter < iterations; counter++) {
            OffsetDateTime time = startTime.plusMinutes(counter);
            BigDecimal value = startValue.add(BigDecimal.valueOf(batchSize * concurrency * counter));
            // Closing executor waits for all tasks to complete
            try (ExecutorService executor = Executors.newFixedThreadPool(concurrency)) {
                for (int i = 0; i < concurrency; i++) {
                    int threadIndex = i;
                    executor.execute(() -> {
                        map.beginBatch();
                        map.putAll(IntStream.range(0, batchSize)
                                .mapToObj(j -> kv(keyPrefix + (threadIndex * batchSize + j), tv(time,
                                        value.add(BigDecimal.valueOf(threadIndex * batchSize + j)))))
                                .collect(Collectors.toList()));
                    });
                }
            }

            map.completeBatch();
        }

        OffsetDateTime latestTime = startTime.plusMinutes(iterations - 1);
        BigDecimal startValueLastIteration = startValue.add(BigDecimal.valueOf(batchSize * concurrency * (iterations - 1)));
        for (int i = 0; i < concurrency * batchSize; i++) {
            var expectedValue = tv(latestTime, startValueLastIteration.add(BigDecimal.valueOf(i)));
            var actualValue = map.get(keyPrefix + i);
            assertEquals(expectedValue, actualValue);
        }
    }

    @ParameterizedTest
    @ArgumentsSource(BatchTimeMapProvider.class)
    public void doNotBlockInConcurrentScenario(ConcurrentBatchMap<String, TimestampedValue<BigDecimal>> map) {

        String key = "X";
        OffsetDateTime startTime = OffsetDateTime.now();
        BigDecimal startValue = BigDecimal.valueOf(1);
        int concurrency = 50;
        int iterations = 10;
        Random randGen = new Random();

        try (ExecutorService executor = Executors.newFixedThreadPool(concurrency * iterations)) {

            for (int counter = 0; counter < iterations; counter++) {
                OffsetDateTime time = startTime.plusMinutes(counter);
                BigDecimal value = startValue.add(BigDecimal.valueOf(concurrency * counter));

                for (int i = 0; i < concurrency; i++) {

                    executor.execute(() -> {
                        try {
                            Thread.sleep(0, randGen.nextInt(1000, 10000));
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        map.beginBatch();
                        Thread.yield();
                        map.putAll(List.of(kv(key, tv(time, value))));
                        map.completeBatch();
                    });
                }
            }

        }

        var actualValue = map.get(key);
        assertNotNull(actualValue);
        assertFalse(actualValue.asOf().isBefore(startTime));
        assertTrue(actualValue.value().compareTo(startValue) >= 0);
    }
}