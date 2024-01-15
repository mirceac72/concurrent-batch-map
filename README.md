# Concurrent Batch Map

The purpose of this repository is to illustrate that designing concurrent data structures
that exhibit a low granularity of concurrency is not a trivial task, requiring careful 
designing and a significant time investment.

Let us consider the case of the maps/dictionaries with a twist, an update into these
maps will not become available immediately for reading. The updates will become available
in batches. To draw a parallel with the database transactions, we aim to provide atomicity
for groups of updates. These groups of updates will succeed or fail together. 
To describe this class of maps, we defined the interface `ConcurrentBatchMap` that 
contains three specific methods:
- `beginBatch` - starts a group of updates that will succeed or fail together.
- `completeBatch` - finalize the current group of updates and makes the updates visible in 
`get` operations 
- `cancelBatch` - drops all the updates from the current batch.

Due to this twist, we cannot use directly the existent 
[ConcurrentMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentMap.html)
implementations as there is no way to group multiple changes and make them
available at once. So we have to provide our own implementations,
perhaps using `ConcurrentMap` internally.

Two implementations of this interface, `BlockingBatchMap` and `LowBlockingBatchMap`
are using two internal maps, a `readMap` and `writeMap`. The get operations are
served using the `readMap` while the puts are made into the `writeMap`.
When the current batch completes successfully, the `writeMap` becomes the
`readMap` and when a new batch starts then a new `writeMap` is created. This
solves the problem of making all updates from a batch available together.

## BlockingBatchMap

`BlockingBatchMap` implementation has a conservative, coarse granularity approach on concurrence
by using `synchronized` on all methods that are modifying the map. Thus, only one of 
these methods can be executing at any point in time. This approach is easy to comprehend,
develop and maintain. It has the disadvantage that it is actually a serial implementation,
only a single thread is able to change the map at any point in time.

Let us assume that we want to achieve a fine granularity on concurrency:
- multiple threads are able to perform put operations in the same time.
- a `completeBatch` or a `cancelBatch` operation can be started while other
threads are performing `put` operations. `completeBatch` will wait until
the other threads have finished the updates before making all changes 
available for the `get` operations.
- a `beginBatch` operation for a new batch can start while a `completeBatch` or `cancelBatch` 
operation for the previous batch is running. Performing changes in the new batch can start before
the previous batch to be made visible.

## LowBlockingBatchMap

`LowBlockingBatchMap` implementation achieves the aforementioned goals by
reducing the scope of the `synchronized` blocks to minimum.
- A [ConcurrentMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentMap.html) 
implementation is used for the `writeMap`. This allows for multiple threads to
make concurrent put operations into `writeMap`.
- No put operation or map content copy operation is made within a
synchronized block. The period of time spent into a synchronized block should
not depend on the number of pairs already existing in the map or on the number pairs that
are changed by an operation.

This approach is posing the following challenges: 
- `completeBatch` operation should know when all the threads performing
put operations completed in order to make all the changes visible. Otherwise, 
there is the risk to miss some changes or make some changes in the batch 
available later.
- A `put` operation can start at any time before `completeBatch` to start
and there will be an arbitrary number of `put` operations.
- A `completeBatch` can finish only after the previous `completeBatch` 
finished and all the changes from the previous batch were incorporated.

In order to align the thread performing `completeBatch` with the
threads modifying the `writeMap`, a 
[Phaser](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Phaser.html)
barrier will be used. We need a phaser because we do not know how many threads we have
to synchronize at the moment when the barrier is created. Each batch will use
its own `Phaser` instance that will be created on `beginBatch`. `beginBatch` will
also register to the `Phaser` instance of the previous batch in order to wait
until all changes made by the previous batch were incorporated.
As expected, `completeBatch` will wait until all threads registered to the `Phaser`
instance associated with the batch have arrived and the `writeMap` will be set
as the new `readMap` within the Phaser `onActivate` method call.

`volatile` specifier is used for `readMap`, `writeMap` and `phaser` fields from
`LowBlockingBatchMap`. This establishes a 
[happens-before](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4.5) 
relation between the operations writing these fields and the subsequent reads. 

Methods `beginBatch`, `put`, `completeBatch` make local copies of the shared references like
`writeMap` and `phaser` within the `synchronized` sections and use these copies afterward
in order to do not be impacted by other threads concurrently changing these references.

