package org.example;

public record KeyValue<K, V>(K key, V value) {
    public static <K, V> KeyValue<K, V> kv(K key, V value) {
        return new KeyValue<>(key, value);
    }
}
