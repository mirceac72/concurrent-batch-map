package org.example;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class BlockingBatchMap<K, V> implements ConcurrentBatchMap<K, V> {

    private volatile Map<K, V> readMap;
    private volatile Map<K, V> writeMap;

    public BlockingBatchMap() {
        readMap = Map.of();
        writeMap = null;
    }

    public V get(K key) {
        return readMap.get(key);
    }

    public synchronized void beginBatch() {

        if (writeMap != null) {
            return;
        }
        writeMap = new HashMap<>(readMap);
    }

    public synchronized boolean putAll(Collection<KeyValue<K, V>> values) {

        if (writeMap == null) {
            return false;
        }
        for (var kv : values) {
            writeMap.merge(kv.key(), kv.value(), this::mergeFn);
        }
        return true;
    }

    public synchronized void completeBatch() {
            if (writeMap == null) {
                return;
            }
            readMap = writeMap;
            writeMap = null;
    }

    public synchronized void cancelBatch() {
            writeMap = null;
    }
}
