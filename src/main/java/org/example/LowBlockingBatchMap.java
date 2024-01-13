package org.example;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Phaser;

/**
 * An implementation of BatchTimeMap that aims to keep synchronized sections at minimum.
 * As BlockingBatchTimeMap does, this implementation uses a write map to collect the
 * changes made by the batch and, when the batch completes, it replaces the read map with the
 * write map in order to make the changes visible.
 * In order to keep synchronized sections as small as possible, the implementation will not use
 * synchronized statements while merging changes into the write map and the type of the used
 * write map supports concurrent updates.
 * Multiple threads are able to write in batch concurrently. completeBatch() will wait until
 * the producer threads published all changes before making the changes visible. beginBatch()
 * will wait until the changes from the previous batch were published before finishing.
 *
 * @param <K>
 * @param <V>
 */
public class LowBlockingBatchMap<K, V> implements ConcurrentBatchMap<K, V> {
    private volatile Map<K, V> readMap;
    private volatile ConcurrentMap<K, V> writeMap;
    private volatile CopyOnAdvancePhaser phaser;

    public LowBlockingBatchMap() {

        readMap = Map.of();
        writeMap = null;
        phaser = null;
    }

    public V get(K key) {
        return readMap.get(key);
    }

    public void beginBatch() {
        CopyOnAdvancePhaser previousBatchPhaser;
        CopyOnAdvancePhaser newBatchPhaser;
        ConcurrentMap<K, V> newBatchWriteMap;
        synchronized (this) {
            // Check if there is a writeMap. If yes then a batch is in progress
            // and return immediately
            if (Objects.nonNull(writeMap)) {
                return;
            }
            newBatchWriteMap = new ConcurrentHashMap<>();
            writeMap = newBatchWriteMap;
            previousBatchPhaser = phaser;
            newBatchPhaser = new CopyOnAdvancePhaser(newBatchWriteMap);
            phaser = newBatchPhaser;
            if (previousBatchPhaser != null) {
                previousBatchPhaser.register();
            }
        }
        if (previousBatchPhaser != null) {
            // Await previous batch to complete and expose its changes in readMap
            previousBatchPhaser.awaitAdvance(previousBatchPhaser.arriveAndDeregister());
        }
        // Based on the assumption that there is a large number of existing mapping
        // parallelization is considered beneficial
        readMap.entrySet()
                .parallelStream()
                .forEach(tkv -> newBatchWriteMap.merge(tkv.getKey(), tkv.getValue(), this::mergeFn));
        newBatchPhaser.arriveAndDeregister();
    }

    public boolean putAll(Collection<KeyValue<K, V>> values) {

        Phaser currentBatchPhaser;
        ConcurrentMap<K, V> currentBatchWriteMap;
        synchronized (this) {
            // If there is no writeMap then no batch is in progress and return immediately
            if (Objects.isNull(writeMap)) {
                return false;
            }
            phaser.register();
            currentBatchPhaser = phaser;
            currentBatchWriteMap = writeMap;
        }
        for (var kv : values) {
            currentBatchWriteMap.merge(kv.key(), kv.value(), this::mergeFn);
        }
        currentBatchPhaser.arriveAndDeregister();
        return true;
    }

    public void completeBatch() {
        endBatch(true);
    }

    public void cancelBatch() {
        endBatch(false);
    }

    private void endBatch(boolean makeAvailable) {
        CopyOnAdvancePhaser endingBatchPhaser;
        synchronized (this) {
            // If there is no writeMap then no batch is in progress and return immediately
            if (Objects.isNull(writeMap)) {
                return;
            }
            endingBatchPhaser = phaser;
            writeMap = null;
        }
        endingBatchPhaser.copy(makeAvailable);
        endingBatchPhaser.awaitAdvance(endingBatchPhaser.arriveAndDeregister());
    }

    private class CopyOnAdvancePhaser extends Phaser {

        final ConcurrentMap<K, V> newBatchWriteMap;
        private volatile boolean copyOnAdvance;

        public CopyOnAdvancePhaser(ConcurrentMap<K, V> writeMap) {

            super(2);
            newBatchWriteMap = writeMap;
            copyOnAdvance = false;
        }

        public void copy(boolean copyOnAdvance) {
            this.copyOnAdvance = copyOnAdvance;
        }

        @Override
        protected boolean onAdvance(int phase, int registeredParties) {
            if (registeredParties == 0) {
                if (copyOnAdvance) {
                    readMap = newBatchWriteMap;
                }
                return true;
            }
            return false;
        }
    }
}
