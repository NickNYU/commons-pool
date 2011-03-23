/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.pool2;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * This class consists exclusively of static methods that operate on or return ObjectPool
 * or KeyedObjectPool related interfaces.
 *
 * @author Sandy McArthur
 * @version $Revision$ $Date$
 * @since Pool 1.3
 */
public final class PoolUtils {

    /**
     * Timer used to periodically check pools idle object count.
     * Because a {@link Timer} creates a {@link Thread} this is lazily instantiated.
     */
    private static Timer MIN_IDLE_TIMER; //@GuardedBy("this")

    /**
     * PoolUtils instances should NOT be constructed in standard programming.
     * Instead, the class should be used procedurally: PoolUtils.adapt(aPool);.
     * This constructor is public to permit tools that require a JavaBean instance to operate.
     */
    public PoolUtils() {
    }

    /**
     * Should the supplied Throwable be re-thrown (eg if it is an instance of
     * one of the Throwables that should never be swallowed). Used by the pool
     * error handling for operations that throw exceptions that normally need to
     * be ignored.
     * @param t The Throwable to check
     * @throws ThreadDeath if that is passed in
     * @throws VirtualMachineError if that is passed in
     * @since Pool 1.5.5
     */
    public static void checkRethrow(Throwable t) {
        if (t instanceof ThreadDeath) {
            throw (ThreadDeath) t;
        }
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        }
        // All other instances of Throwable will be silently swallowed
    }

    /**
     * Periodically check the idle object count for the pool. At most one idle object will be added per period.
     * If there is an exception when calling {@link ObjectPool#addObject()} then no more checks will be performed.
     *
     * @param pool the pool to check periodically.
     * @param minIdle if the {@link ObjectPool#getNumIdle()} is less than this then add an idle object.
     * @param period the frequency to check the number of idle objects in a pool, see
     *      {@link Timer#schedule(TimerTask, long, long)}.
     * @return the {@link TimerTask} that will periodically check the pools idle object count.
     * @throws IllegalArgumentException when <code>pool</code> is <code>null</code> or
     *      when <code>minIdle</code> is negative or when <code>period</code> isn't
     *      valid for {@link Timer#schedule(TimerTask, long, long)}.
     * @since Pool 1.3
     */
    public static <T> TimerTask checkMinIdle(final ObjectPool<T> pool, final int minIdle, final long period) throws IllegalArgumentException {
        if (pool == null) {
            throw new IllegalArgumentException("keyedPool must not be null.");
        }
        if (minIdle < 0) {
            throw new IllegalArgumentException("minIdle must be non-negative.");
        }
        final TimerTask task = new ObjectPoolMinIdleTimerTask<T>(pool, minIdle);
        getMinIdleTimer().schedule(task, 0L, period);
        return task;
    }

    /**
     * Periodically check the idle object count for the key in the keyedPool. At most one idle object will be added per period.
     * If there is an exception when calling {@link KeyedObjectPool#addObject(Object)} then no more checks for that key
     * will be performed.
     *
     * @param keyedPool the keyedPool to check periodically.
     * @param key the key to check the idle count of.
     * @param minIdle if the {@link KeyedObjectPool#getNumIdle(Object)} is less than this then add an idle object.
     * @param period the frequency to check the number of idle objects in a keyedPool, see
     *      {@link Timer#schedule(TimerTask, long, long)}.
     * @return the {@link TimerTask} that will periodically check the pools idle object count.
     * @throws IllegalArgumentException when <code>keyedPool</code>, <code>key</code> is <code>null</code> or
     *      when <code>minIdle</code> is negative or when <code>period</code> isn't
     *      valid for {@link Timer#schedule(TimerTask, long, long)}.
     * @since Pool 1.3
     */
    public static <K,V> TimerTask checkMinIdle(final KeyedObjectPool<K,V> keyedPool, final K key, final int minIdle, final long period) throws IllegalArgumentException {
        if (keyedPool == null) {
            throw new IllegalArgumentException("keyedPool must not be null.");
        }
        if (key == null) {
            throw new IllegalArgumentException("key must not be null.");
        }
        if (minIdle < 0) {
            throw new IllegalArgumentException("minIdle must be non-negative.");
        }
        final TimerTask task = new KeyedObjectPoolMinIdleTimerTask<K,V>(keyedPool, key, minIdle);
        getMinIdleTimer().schedule(task, 0L, period);
        return task;
    }

    /**
     * Periodically check the idle object count for each key in the <code>Collection</code> <code>keys</code> in the keyedPool.
     * At most one idle object will be added per period.
     *
     * @param keyedPool the keyedPool to check periodically.
     * @param keys a collection of keys to check the idle object count.
     * @param minIdle if the {@link KeyedObjectPool#getNumIdle(Object)} is less than this then add an idle object.
     * @param period the frequency to check the number of idle objects in a keyedPool, see
     *      {@link Timer#schedule(TimerTask, long, long)}.
     * @return a {@link Map} of key and {@link TimerTask} pairs that will periodically check the pools idle object count.
     * @throws IllegalArgumentException when <code>keyedPool</code>, <code>keys</code>, or any of the values in the
     *      collection is <code>null</code> or when <code>minIdle</code> is negative or when <code>period</code> isn't
     *      valid for {@link Timer#schedule(TimerTask, long, long)}.
     * @see #checkMinIdle(KeyedObjectPool, Object, int, long)
     * @since Pool 1.3
     */
    public static <K,V> Map<K,TimerTask> checkMinIdle(final KeyedObjectPool<K,V> keyedPool, final Collection<K> keys, final int minIdle, final long period) throws IllegalArgumentException {
        if (keys == null) {
            throw new IllegalArgumentException("keys must not be null.");
        }
        final Map<K,TimerTask> tasks = new HashMap<K,TimerTask>(keys.size());
        for (K key : keys) {
            tasks.put(key, checkMinIdle(keyedPool, key, minIdle, period));            
        } 
        return tasks;
    }

    /**
     * Calls {@link ObjectPool#addObject()} on <code>pool</code> <code>count</code> number of times.
     *
     * @param pool the pool to prefill.
     * @param count the number of idle objects to add.
     * @throws Exception when {@link ObjectPool#addObject()} fails.
     * @throws IllegalArgumentException when <code>pool</code> is <code>null</code>.
     * @since Pool 1.3
     */
    public static <T> void prefill(final ObjectPool<T> pool, final int count) throws Exception, IllegalArgumentException {
        if (pool == null) {
            throw new IllegalArgumentException("pool must not be null.");
        }
        for (int i = 0; i < count; i++) {
            pool.addObject();
        }
    }

    /**
     * Calls {@link ObjectPool#addObject()} on <code>keyedPool</code> with <code>key</code> <code>count</code>
     * number of times.
     *
     * @param keyedPool the keyedPool to prefill.
     * @param key the key to add objects for.
     * @param count the number of idle objects to add for <code>key</code>.
     * @throws Exception when {@link KeyedObjectPool#addObject(Object)} fails.
     * @throws IllegalArgumentException when <code>keyedPool</code> or <code>key</code> is <code>null</code>.
     * @since Pool 1.3
     */
    public static <K,V> void prefill(final KeyedObjectPool<K,V> keyedPool, final K key, final int count) throws Exception, IllegalArgumentException {
        if (keyedPool == null) {
            throw new IllegalArgumentException("keyedPool must not be null.");
        }
        if (key == null) {
            throw new IllegalArgumentException("key must not be null.");
        }
        for (int i = 0; i < count; i++) {
            keyedPool.addObject(key);
        }
    }

    /**
     * Calls {@link ObjectPool#addObject()} on <code>keyedPool</code> with each key in <code>keys</code> for
     * <code>count</code> number of times. This has the same effect as calling
     * {@link #prefill(KeyedObjectPool, Object, int)} for each key in the <code>keys</code> collection.
     *
     * @param keyedPool the keyedPool to prefill.
     * @param keys {@link Collection} of keys to add objects for.
     * @param count the number of idle objects to add for each <code>key</code>.
     * @throws Exception when {@link KeyedObjectPool#addObject(Object)} fails.
     * @throws IllegalArgumentException when <code>keyedPool</code>, <code>keys</code>, or
     *      any value in <code>keys</code> is <code>null</code>.
     * @see #prefill(KeyedObjectPool, Object, int)
     * @since Pool 1.3
     */
    public static <K,V> void prefill(final KeyedObjectPool<K,V> keyedPool, final Collection<K> keys, final int count) throws Exception, IllegalArgumentException {
        if (keys == null) {
            throw new IllegalArgumentException("keys must not be null.");
        }
        for (K key : keys) {
            prefill(keyedPool, key, count);
        }
    }

    /**
     * Returns a synchronized (thread-safe) ObjectPool backed by the specified ObjectPool.
     *
     * <p><b>Note:</b>
     * This should not be used on pool implementations that already provide proper synchronization
     * such as the pools provided in the Commons Pool library. Wrapping a pool that
     * {@link #wait() waits} for poolable objects to be returned before allowing another one to be
     * borrowed with another layer of synchronization will cause liveliness issues or a deadlock.
     * </p>
     *
     * @param pool the ObjectPool to be "wrapped" in a synchronized ObjectPool.
     * @return a synchronized view of the specified ObjectPool.
     * @since Pool 1.3
     */
    @SuppressWarnings("unchecked") // returned proxy is a ObjectPool<T> wrapper
    public static <T> ObjectPool<T> synchronizedPool(final ObjectPool<T> pool) {
        if (pool == null) {
            throw new IllegalArgumentException("pool must not be null.");
        }
        /*
        assert !(pool instanceof GenericObjectPool)
                : "GenericObjectPool is already thread-safe";
        assert !(pool instanceof SoftReferenceObjectPool)
                : "SoftReferenceObjectPool is already thread-safe";
        assert !(pool instanceof StackObjectPool)
                : "StackObjectPool is already thread-safe";
        assert !"org.apache.commons.pool.composite.CompositeObjectPool".equals(pool.getClass().getName())
                : "CompositeObjectPools are already thread-safe";
        */
        return synchronizedObject(pool, ObjectPool.class);
    }

    /**
     * Returns a synchronized (thread-safe) KeyedObjectPool backed by the specified KeyedObjectPool.
     *
     * <p><b>Note:</b>
     * This should not be used on pool implementations that already provide proper synchronization
     * such as the pools provided in the Commons Pool library. Wrapping a pool that
     * {@link #wait() waits} for poolable objects to be returned before allowing another one to be
     * borrowed with another layer of synchronization will cause liveliness issues or a deadlock.
     * </p>
     *
     * @param keyedPool the KeyedObjectPool to be "wrapped" in a synchronized KeyedObjectPool.
     * @return a synchronized view of the specified KeyedObjectPool.
     * @since Pool 1.3
     */
    @SuppressWarnings("unchecked") // returned proxy is a KeyedObjectPool<K,V> wrapper
    public static <K,V> KeyedObjectPool<K,V> synchronizedPool(final KeyedObjectPool<K,V> keyedPool) {
        if (keyedPool == null) {
            throw new IllegalArgumentException("keyedPool must not be null.");
        }
        /*
        assert !(keyedPool instanceof GenericKeyedObjectPool)
                : "GenericKeyedObjectPool is already thread-safe";
        assert !(keyedPool instanceof StackKeyedObjectPool)
                : "StackKeyedObjectPool is already thread-safe";
        assert !"org.apache.commons.pool.composite.CompositeKeyedObjectPool".equals(keyedPool.getClass().getName())
                : "CompositeKeyedObjectPools are already thread-safe";
        */
        return synchronizedObject(keyedPool, KeyedObjectPool.class);
    }

    /**
     * Returns a synchronized (thread-safe) PoolableObjectFactory backed by the specified PoolableObjectFactory.
     *
     * @param factory the PoolableObjectFactory to be "wrapped" in a synchronized PoolableObjectFactory.
     * @return a synchronized view of the specified PoolableObjectFactory.
     * @since Pool 1.3
     */
    @SuppressWarnings("unchecked") // returned proxy is a PoolableObjectFactory<T> wrapper
    public static <T> PoolableObjectFactory<T> synchronizedPoolableFactory(final PoolableObjectFactory<T> factory) {
        if (factory == null) {
            throw new IllegalArgumentException("factory must not be null.");
        }
        return synchronizedObject(factory, PoolableObjectFactory.class);
    }

    /**
     * Returns a synchronized (thread-safe) KeyedPoolableObjectFactory backed by the specified KeyedPoolableObjectFactory.
     *
     * @param keyedFactory the KeyedPoolableObjectFactory to be "wrapped" in a synchronized KeyedPoolableObjectFactory.
     * @return a synchronized view of the specified KeyedPoolableObjectFactory.
     * @since Pool 1.3
     */
    @SuppressWarnings("unchecked") // returned proxy is a KeyedPoolableObjectFactory<K,V> wrapper
    public static <K,V> KeyedPoolableObjectFactory<K,V> synchronizedPoolableFactory(final KeyedPoolableObjectFactory<K,V> keyedFactory) {
        if (keyedFactory == null) {
            throw new IllegalArgumentException("keyedFactory must not be null.");
        }
        return synchronizedObject(keyedFactory, KeyedPoolableObjectFactory.class);
    }

    /**
     * Wrap the given object in a proxed one where all methods declared in the given interface will be synchronized.
     *
     * @param <T> the object type has to be proxed
     * @param toBeSynchronized to object which methods have to be synchronized
     * @param type the interface has to be proxed
     * @return the dynamic synchronized proxy
     */
    private static <T> T synchronizedObject(final T toBeSynchronized, final Class<T> type) {
        /*
         * Used to synchronize method declared on the pool/factory interface only.
         */
        final Set<Method> synchronizedMethods = new HashSet<Method>();

        for (Method method : type.getDeclaredMethods()) {
            synchronizedMethods.add(method);
        }

        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),
                new Class<?>[] { type },
                new InvocationHandler() {

                    private final Object lock = new Object();

                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (synchronizedMethods.contains(method)) {
                            synchronized (this.lock) {
                                return method.invoke(toBeSynchronized, args);
                            }
                        }
                        return method.invoke(toBeSynchronized, args);
                    }

                }
        ));
    }

    /**
     * Returns a pool that adaptively decreases it's size when idle objects are no longer needed.
     * This is intended as an always thread-safe alternative to using an idle object evictor
     * provided by many pool implementations. This is also an effective way to shrink FIFO ordered
     * pools that experience load spikes.
     *
     * @param pool the ObjectPool to be decorated so it shrinks it's idle count when possible.
     * @return a pool that adaptively decreases it's size when idle objects are no longer needed.
     * @see #erodingPool(ObjectPool, float)
     * @since Pool 1.4
     */
    public static <T> ObjectPool<T> erodingPool(final ObjectPool<T> pool) {
        return erodingPool(pool, 1f);
    }

    /**
     * Returns a pool that adaptively decreases it's size when idle objects are no longer needed.
     * This is intended as an always thread-safe alternative to using an idle object evictor
     * provided by many pool implementations. This is also an effective way to shrink FIFO ordered
     * pools that experience load spikes.
     *
     * <p>
     * The factor parameter provides a mechanism to tweak the rate at which the pool tries to shrink
     * it's size. Values between 0 and 1 cause the pool to try to shrink it's size more often.
     * Values greater than 1 cause the pool to less frequently try to shrink it's size.
     * </p>
     *
     * @param pool the ObjectPool to be decorated so it shrinks it's idle count when possible.
     * @param factor a positive value to scale the rate at which the pool tries to reduce it's size.
     * If 0 &lt; factor &lt; 1 then the pool shrinks more aggressively.
     * If 1 &lt; factor then the pool shrinks less aggressively.
     * @return a pool that adaptively decreases it's size when idle objects are no longer needed.
     * @see #erodingPool(ObjectPool)
     * @since Pool 1.4
     */
    public static <T> ObjectPool<T> erodingPool(final ObjectPool<T> pool, final float factor) {
        if (pool == null) {
            throw new IllegalArgumentException("pool must not be null.");
        }
        if (factor <= 0f) {
            throw new IllegalArgumentException("factor must be positive.");
        }
        return new ErodingObjectPool<T>(pool, factor);
    }

    /**
     * Returns a pool that adaptively decreases it's size when idle objects are no longer needed.
     * This is intended as an always thread-safe alternative to using an idle object evictor
     * provided by many pool implementations. This is also an effective way to shrink FIFO ordered
     * pools that experience load spikes.
     *
     * @param keyedPool the KeyedObjectPool to be decorated so it shrinks it's idle count when
     * possible.
     * @return a pool that adaptively decreases it's size when idle objects are no longer needed.
     * @see #erodingPool(KeyedObjectPool, float)
     * @see #erodingPool(KeyedObjectPool, float, boolean)
     * @since Pool 1.4
     */
    public static <K,V> KeyedObjectPool<K,V> erodingPool(final KeyedObjectPool<K,V> keyedPool) {
        return erodingPool(keyedPool, 1f);
    }

    /**
     * Returns a pool that adaptively decreases it's size when idle objects are no longer needed.
     * This is intended as an always thread-safe alternative to using an idle object evictor
     * provided by many pool implementations. This is also an effective way to shrink FIFO ordered
     * pools that experience load spikes.
     *
     * <p>
     * The factor parameter provides a mechanism to tweak the rate at which the pool tries to shrink
     * it's size. Values between 0 and 1 cause the pool to try to shrink it's size more often.
     * Values greater than 1 cause the pool to less frequently try to shrink it's size.
     * </p>
     *
     * @param keyedPool the KeyedObjectPool to be decorated so it shrinks it's idle count when
     * possible.
     * @param factor a positive value to scale the rate at which the pool tries to reduce it's size.
     * If 0 &lt; factor &lt; 1 then the pool shrinks more aggressively.
     * If 1 &lt; factor then the pool shrinks less aggressively.
     * @return a pool that adaptively decreases it's size when idle objects are no longer needed.
     * @see #erodingPool(KeyedObjectPool, float, boolean)
     * @since Pool 1.4
     */
    public static <K,V> KeyedObjectPool<K,V> erodingPool(final KeyedObjectPool<K,V> keyedPool, final float factor) {
        return erodingPool(keyedPool, factor, false);
    }

    /**
     * Returns a pool that adaptively decreases it's size when idle objects are no longer needed.
     * This is intended as an always thread-safe alternative to using an idle object evictor
     * provided by many pool implementations. This is also an effective way to shrink FIFO ordered
     * pools that experience load spikes.
     *
     * <p>
     * The factor parameter provides a mechanism to tweak the rate at which the pool tries to shrink
     * it's size. Values between 0 and 1 cause the pool to try to shrink it's size more often.
     * Values greater than 1 cause the pool to less frequently try to shrink it's size.
     * </p>
     *
     * <p>
     * The perKey parameter determines if the pool shrinks on a whole pool basis or a per key basis.
     * When perKey is false, the keys do not have an effect on the rate at which the pool tries to
     * shrink it's size. When perKey is true, each key is shrunk independently.
     * </p>
     *
     * @param keyedPool the KeyedObjectPool to be decorated so it shrinks it's idle count when
     * possible.
     * @param factor a positive value to scale the rate at which the pool tries to reduce it's size.
     * If 0 &lt; factor &lt; 1 then the pool shrinks more aggressively.
     * If 1 &lt; factor then the pool shrinks less aggressively.
     * @param perKey when true, each key is treated independently.
     * @return a pool that adaptively decreases it's size when idle objects are no longer needed.
     * @see #erodingPool(KeyedObjectPool)
     * @see #erodingPool(KeyedObjectPool, float)
     * @since Pool 1.4
     */
    public static <K,V> KeyedObjectPool<K,V> erodingPool(final KeyedObjectPool<K,V> keyedPool, final float factor, final boolean perKey) {
        if (keyedPool == null) {
            throw new IllegalArgumentException("keyedPool must not be null.");
        }
        if (factor <= 0f) {
            throw new IllegalArgumentException("factor must be positive.");
        }
        if (perKey) {
            return new ErodingPerKeyKeyedObjectPool<K,V>(keyedPool, factor);
        } else {
            return new ErodingKeyedObjectPool<K,V>(keyedPool, factor);
        }
    }

    /**
     * Get the <code>Timer</code> for checking keyedPool's idle count. Lazily create the {@link Timer} as needed.
     *
     * @return the {@link Timer} for checking keyedPool's idle count.
     * @since Pool 1.3
     */
    private static synchronized Timer getMinIdleTimer() {
        if (MIN_IDLE_TIMER == null) {
            MIN_IDLE_TIMER = new Timer(true);
        }
        return MIN_IDLE_TIMER;
    }

    /**
     * Timer task that adds objects to the pool until the number of idle
     * instances reaches the configured minIdle.  Note that this is not the
     * same as the pool's minIdle setting.
     * 
     */
    private static class ObjectPoolMinIdleTimerTask<T> extends TimerTask {

        /** Minimum number of idle instances.  Not the same as pool.getMinIdle(). */
        private final int minIdle;

        /** Object pool */
        private final ObjectPool<T> pool;

        /**
         * Create a new ObjectPoolMinIdleTimerTask for the given pool with the given minIdle setting.
         * 
         * @param pool object pool
         * @param minIdle number of idle instances to maintain
         * @throws IllegalArgumentException if the pool is null
         */
        ObjectPoolMinIdleTimerTask(final ObjectPool<T> pool, final int minIdle) throws IllegalArgumentException {
            if (pool == null) {
                throw new IllegalArgumentException("pool must not be null.");
            }
            this.pool = pool;
            this.minIdle = minIdle;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            boolean success = false;
            try {
                if (pool.getNumIdle() < minIdle) {
                    pool.addObject();
                }
                success = true;

            } catch (Exception e) {
                cancel();

            } finally {
                // detect other types of Throwable and cancel this Timer
                if (!success) {
                    cancel();
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            final StringBuffer sb = new StringBuffer();
            sb.append("ObjectPoolMinIdleTimerTask");
            sb.append("{minIdle=").append(minIdle);
            sb.append(", pool=").append(pool);
            sb.append('}');
            return sb.toString();
        }
    }

    /**
     * Timer task that adds objects to the pool until the number of idle
     * instances for the given key reaches the configured minIdle.  Note that this is not the
     * same as the pool's minIdle setting.
     * 
     */
    private static class KeyedObjectPoolMinIdleTimerTask<K,V> extends TimerTask {
        /** Minimum number of idle instances.  Not the same as pool.getMinIdle(). */
        private final int minIdle;

        /** Key to ensure minIdle for */
        private final K key;

        /** Keyed object pool */
        private final KeyedObjectPool<K,V> keyedPool;

        /**
         * Create a new KeyedObjecPoolMinIdleTimerTask.
         * 
         * @param keyedPool keyed object pool
         * @param key key to ensure minimum number of idle instances
         * @param minIdle minimum number of idle instances 
         * @throws IllegalArgumentException if the key is null
         */
        KeyedObjectPoolMinIdleTimerTask(final KeyedObjectPool<K,V> keyedPool, final K key, final int minIdle) throws IllegalArgumentException {
            if (keyedPool == null) {
                throw new IllegalArgumentException("keyedPool must not be null.");
            }
            this.keyedPool = keyedPool;
            this.key = key;
            this.minIdle = minIdle;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            boolean success = false;
            try {
                if (keyedPool.getNumIdle(key) < minIdle) {
                    keyedPool.addObject(key);
                }
                success = true;

            } catch (Exception e) {
                cancel();

            } finally {
                // detect other types of Throwable and cancel this Timer
                if (!success) {
                    cancel();
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            final StringBuffer sb = new StringBuffer();
            sb.append("KeyedObjectPoolMinIdleTimerTask");
            sb.append("{minIdle=").append(minIdle);
            sb.append(", key=").append(key);
            sb.append(", keyedPool=").append(keyedPool);
            sb.append('}');
            return sb.toString();
        }
    }

    /**
     * Encapsulate the logic for when the next poolable object should be discarded.
     * Each time update is called, the next time to shrink is recomputed, based on
     * the float factor, number of idle instances in the pool and high water mark.
     * Float factor is assumed to be between 0 and 1.  Values closer to 1 cause
     * less frequent erosion events.  Erosion event timing also depends on numIdle.
     * When this value is relatively high (close to previously established high water
     * mark), erosion occurs more frequently.
     */
    private static class ErodingFactor {
        /** Determines frequency of "erosion" events */
        private final float factor;

        /** Time of next shrink event */
        private transient volatile long nextShrink;

        /** High water mark - largest numIdle encountered */
        private transient volatile int idleHighWaterMark;

        /**
         * Create a new ErodingFactor with the given erosion factor.
         * 
         * @param factor erosion factor
         */
        public ErodingFactor(final float factor) {
            this.factor = factor;
            nextShrink = System.currentTimeMillis() + (long)(900000 * factor); // now + 15 min * factor
            idleHighWaterMark = 1;
        }

        /**
         * Updates internal state based on numIdle and the current time.
         * 
         * @param numIdle number of idle elements in the pool
         */
        public void update(final int numIdle) {
            update(System.currentTimeMillis(), numIdle);
        }

        /**
         * Updates internal state using the supplied time and numIdle.
         * 
         * @param now current time
         * @param numIdle number of idle elements in the pool
         */
        public void update(final long now, final int numIdle) {
            final int idle = Math.max(0, numIdle);
            idleHighWaterMark = Math.max(idle, idleHighWaterMark);
            final float maxInterval = 15f;
            final float minutes = maxInterval + ((1f-maxInterval)/idleHighWaterMark) * idle;
            nextShrink = now + (long)(minutes * 60000f * factor);
        }

        /**
         * Returns the time of the next erosion event.
         * 
         * @return next shrink time
         */
        public long getNextShrink() {
            return nextShrink;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "ErodingFactor{" +
                    "factor=" + factor +
                    ", idleHighWaterMark=" + idleHighWaterMark +
                    '}';
        }
    }

    /**
     * Decorates an object pool, adding "eroding" behavior.  Based on the
     * configured {@link #factor erosion factor}, objects returning to the pool
     * may be invalidated instead of being added to idle capacity.
     *
     */
    private static class ErodingObjectPool<T> implements ObjectPool<T> {
        /** Underlying object pool */
        private final ObjectPool<T> pool;

        /** Erosion factor */
        private final ErodingFactor factor;

        /** 
         * Create an ErodingObjectPool wrapping the given pool using the specified erosion factor.
         * 
         * @param pool underlying pool
         * @param factor erosion factor - determines the frequency of erosion events
         * @see #factor
         */
        public ErodingObjectPool(final ObjectPool<T> pool, final float factor) {
            this.pool = pool;
            this.factor = new ErodingFactor(factor);
        }

        /**
         * {@inheritDoc}
         */
        public T borrowObject() throws Exception, NoSuchElementException, IllegalStateException {
            return pool.borrowObject();
        }

        /**
         * Returns obj to the pool, unless erosion is triggered, in which
         * case obj is invalidated.  Erosion is triggered when there are idle instances in 
         * the pool and more than the {@link #factor erosion factor}-determined time has elapsed
         * since the last returnObject activation. 
         * 
         * @param obj object to return or invalidate
         * @see #factor
         */
        public void returnObject(final T obj) {
            boolean discard = false;
            final long now = System.currentTimeMillis();
            synchronized (pool) {
                if (factor.getNextShrink() < now) { // XXX: Pool 3: move test out of sync block
                    final int numIdle = pool.getNumIdle();
                    if (numIdle > 0) {
                        discard = true;
                    }

                    factor.update(now, numIdle);
                }
            }
            try {
                if (discard) {
                    pool.invalidateObject(obj);
                } else {
                    pool.returnObject(obj);
                }
            } catch (Exception e) {
                // swallowed
            }
        }

        /**
         * {@inheritDoc}
         */
        public void invalidateObject(final T obj) {
            try {
                pool.invalidateObject(obj);
            } catch (Exception e) {
                // swallowed
            }
        }

        /**
         * {@inheritDoc}
         */
        public void addObject() throws Exception, IllegalStateException, UnsupportedOperationException {
            pool.addObject();
        }

        /**
         * {@inheritDoc}
         */
        public int getNumIdle() throws UnsupportedOperationException {
            return pool.getNumIdle();
        }

        /**
         * {@inheritDoc}
         */
        public int getNumActive() throws UnsupportedOperationException {
            return pool.getNumActive();
        }

        /**
         * {@inheritDoc}
         */
        public void clear() throws Exception, UnsupportedOperationException {
            pool.clear();
        }

        /**
         * {@inheritDoc}
         */
        public void close() {
            try {
                pool.close();
            } catch (Exception e) {
                // swallowed
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "ErodingObjectPool{" +
                    "factor=" + factor +
                    ", pool=" + pool +
                    '}';
        }
    }

    /**
     * Decorates a keyed object pool, adding "eroding" behavior.  Based on the
     * configured {@link #factor erosion factor}, objects returning to the pool
     * may be invalidated instead of being added to idle capacity.
     *
     */
    private static class ErodingKeyedObjectPool<K,V> implements KeyedObjectPool<K,V> {
        /** Underlying pool */
        private final KeyedObjectPool<K,V> keyedPool;

        /** Erosion factor */
        private final ErodingFactor erodingFactor;

        /** 
         * Create an ErodingObjectPool wrapping the given pool using the specified erosion factor.
         * 
         * @param keyedPool underlying pool
         * @param factor erosion factor - determines the frequency of erosion events
         */
        public ErodingKeyedObjectPool(final KeyedObjectPool<K,V> keyedPool, final float factor) {
            this(keyedPool, new ErodingFactor(factor));
        }

        /** 
         * Create an ErodingObjectPool wrapping the given pool using the specified erosion factor.
         * 
         * @param keyedPool underlying pool - must not be null
         * @param erodingFactor erosion factor - determines the frequency of erosion events
         * @see #factor
         */
        protected ErodingKeyedObjectPool(final KeyedObjectPool<K,V> keyedPool, final ErodingFactor erodingFactor) {
            if (keyedPool == null) {
                throw new IllegalArgumentException("keyedPool must not be null.");
            }
            this.keyedPool = keyedPool;
            this.erodingFactor = erodingFactor;
        }

        /**
         * {@inheritDoc}
         */
        public V borrowObject(final K key) throws Exception, NoSuchElementException, IllegalStateException {
            return keyedPool.borrowObject(key);
        }

        /**
         * Returns obj to the pool, unless erosion is triggered, in which
         * case obj is invalidated.  Erosion is triggered when there are idle instances in 
         * the pool associated with the given key and more than the configured {@link #getErodingFactor(Object) erosion factor}
         * time has elapsed since the last returnObject activation. 
         * 
         * @param obj object to return or invalidate
         * @param key key
         * @see #getErodingFactor(Object)
         */
        public void returnObject(final K key, final V obj) throws Exception {
            boolean discard = false;
            final long now = System.currentTimeMillis();
            final ErodingFactor factor = getErodingFactor(key);
            synchronized (keyedPool) {
                if (factor.getNextShrink() < now) {
                    final int numIdle = numIdle(key);
                    if (numIdle > 0) {
                        discard = true;
                    }

                    factor.update(now, numIdle);
                }
            }
            try {
                if (discard) {
                    keyedPool.invalidateObject(key, obj);
                } else {
                    keyedPool.returnObject(key, obj);
                }
            } catch (Exception e) {
                // swallowed
            }
        }

        protected int numIdle(final K key) {
            return getKeyedPool().getNumIdle();
        }

        /**
         * Returns the eroding factor for the given key
         * @param key key
         * @return eroding factor for the given keyed pool
         */
        protected ErodingFactor getErodingFactor(final K key) {
            return erodingFactor;
        }

        /**
         * {@inheritDoc}
         */
        public void invalidateObject(final K key, final V obj) {
            try {
                keyedPool.invalidateObject(key, obj);
            } catch (Exception e) {
                // swallowed
            }
        }

        /**
         * {@inheritDoc}
         */
        public void addObject(final K key) throws Exception, IllegalStateException, UnsupportedOperationException {
            keyedPool.addObject(key);
        }

        /**
         * {@inheritDoc}
         */
        public int getNumIdle() throws UnsupportedOperationException {
            return keyedPool.getNumIdle();
        }

        /**
         * {@inheritDoc}
         */
        public int getNumIdle(final K key) throws UnsupportedOperationException {
            return keyedPool.getNumIdle(key);
        }

        /**
         * {@inheritDoc}
         */
        public int getNumActive() throws UnsupportedOperationException {
            return keyedPool.getNumActive();
        }

        /**
         * {@inheritDoc}
         */
        public int getNumActive(final K key) throws UnsupportedOperationException {
            return keyedPool.getNumActive(key);
        }

        /**
         * {@inheritDoc}
         */
        public void clear() throws Exception, UnsupportedOperationException {
            keyedPool.clear();
        }

        /**
         * {@inheritDoc}
         */
        public void clear(final K key) throws Exception, UnsupportedOperationException {
            keyedPool.clear(key);
        }

        /**
         * {@inheritDoc}
         */
        public void close() {
            try {
                keyedPool.close();
            } catch (Exception e) {
                // swallowed
            }
        }

        /**
         * Returns the underlying pool
         * 
         * @return the keyed pool that this ErodingKeyedObjectPool wraps
         */
        protected KeyedObjectPool<K,V> getKeyedPool() {
            return keyedPool;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "ErodingKeyedObjectPool{" +
                    "erodingFactor=" + erodingFactor +
                    ", keyedPool=" + keyedPool +
                    '}';
        }
    }

    /**
     * Extends ErodingKeyedObjectPool to allow erosion to take place on a per-key
     * basis.  Timing of erosion events is tracked separately for separate keyed pools.
     */
    private static class ErodingPerKeyKeyedObjectPool<K,V> extends ErodingKeyedObjectPool<K,V> {
        /** Erosion factor - same for all pools */
        private final float factor;

        /** Map of ErodingFactor instances keyed on pool keys */
        private final Map<K,ErodingFactor> factors = Collections.synchronizedMap(new HashMap<K,ErodingFactor>());

        /**
         * Create a new ErordingPerKeyKeyedObjectPool decorating the given keyed pool with
         * the specified erosion factor.
         * @param keyedPool underlying keyed pool
         * @param factor erosion factor
         */
        public ErodingPerKeyKeyedObjectPool(final KeyedObjectPool<K,V> keyedPool, final float factor) {
            super(keyedPool, null);
            this.factor = factor;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected int numIdle(final K key) {
            return getKeyedPool().getNumIdle(key);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected ErodingFactor getErodingFactor(final K key) {
            ErodingFactor factor = factors.get(key);
            // this may result in two ErodingFactors being created for a key
            // since they are small and cheap this is okay.
            if (factor == null) {
                factor = new ErodingFactor(this.factor);
                factors.put(key, factor);
            }
            return factor;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "ErodingPerKeyKeyedObjectPool{" +
                    "factor=" + factor +
                    ", keyedPool=" + getKeyedPool() +
                    '}';
        }
    }
}