package org.example;

import java.time.OffsetDateTime;

public record TimestampedValue<V>(OffsetDateTime asOf, V value) {

    public static <V> TimestampedValue<V> latest(TimestampedValue<V> first, TimestampedValue<V> second) {

        return first.asOf.isAfter(second.asOf) ? first : second;
    }

    public static <V> TimestampedValue<V> tv(OffsetDateTime asOf, V value) {
        return new TimestampedValue<>(asOf, value);
    }
}
