package org.example;

import java.util.Collection;

/**
 * A variant of map which makes the changes in the map visible in batches.
 * Implementations of this interface should be thread safe.
 * @param <K>
 * @param <V>
 */
public interface ConcurrentBatchMap<K, V> {
    /**
     * Provides the value in the map associated with a key or null if there is
     * no value associated with the specified key.
     * @param key
     * @return
     */
    V get(K key);

    /**
     * Starts a batch update of the map if not already started.
     */
    void beginBatch();


    /**
     * A function used to combine an already existing value for a key with
     * a new value provided for that key in the current set. The default
     * implementation is returning the new value.
     *
     * @param existingValue
     * @param newValue
     * @return
     */
    default V mergeFn(V existingValue, V newValue) {
        return newValue;
    }

    /**
     * Add a collection of key value pairs to the current batch of changes. The changes will become visible
     * only after the batch completes.
     * @param values the set of key values pairs that will be added to the current batch.
     * @return true if there is a batch in progress and the changes were added to the current batch
     * or false if there is no batch in progress.
     */
    boolean putAll(Collection<KeyValue<K, V>> values);
    /**
     * Completes the current batch of changes and makes the changes visible.
     */
    void completeBatch();
    /**
     * Completes the current batch of changes by dropping all changes.
     */
    void cancelBatch();
}
