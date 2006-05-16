/*
 * Copyright 2006 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.pool.composite;

import org.apache.commons.pool.ObjectPool;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.ref.SoftReference;

/**
 * Configures how objects are borrowed and returned to the pool.
 *
 * @see CompositeObjectPoolFactory#setBorrowPolicy(BorrowPolicy)
 * @see CompositeKeyedObjectPoolFactory#setBorrowPolicy(BorrowPolicy)
 * @author Sandy McArthur
 * @since #.#
 * @version $Revision$ $Date$
 */
/* XXX For Pool 3: public enum BorrowPolicy {FIFO, LIFO, SOFT_FIFO, SOFT_LIFO, NULL} but keep existing Javadoc. */
public final class BorrowPolicy implements Serializable {

    private static final long serialVersionUID = 3357921765798594792L;

    /**
     * Objects are returned from the pool in the order they are added to the pool.
     */
    public static final BorrowPolicy FIFO = new BorrowPolicy("FIFO");

    /**
     * The most recently returned object is the first one returned when an object is borrowed.
     */
    public static final BorrowPolicy LIFO = new BorrowPolicy("LIFO");

    /**
     * Like {@link #FIFO} but idle objects are wrapped in a {@link SoftReference} to allow possible garbage
     * collection. Idle objects collected by the garbage collector will not be
     * {@link ObjectPool#invalidateObject(Object) invalidated} like normal.
     */
    public static final BorrowPolicy SOFT_FIFO = new BorrowPolicy("SOFT_FIFO");

    /**
     * Like {@link #LIFO} but idle objects are wrapped in a {@link SoftReference} to allow possible garbage
     * collection. Idle objects collected by the garbage collector will not be
     * {@link ObjectPool#invalidateObject(Object) invalidated} like normal.
     */
    public static final BorrowPolicy SOFT_LIFO = new BorrowPolicy("SOFT_LIFO");

    /**
     * Never returns an object from the pool nor returns one to the pool. This basically turns the pool into a factory,
     * it may have some utility if used with {@link CompositeObjectPoolFactory#setMaxActive(int) maxActive}.
     * This is not compatible with {@link ExhaustionPolicy#FAIL}.
     */
    public static final BorrowPolicy NULL = new BorrowPolicy("NULL");

    /**
     * enum name.
     */
    private final String name;

    private BorrowPolicy(final String name) {
        this.name = name;
    }

    public String toString() {
        return name;
    }

    // Autogenerated with Java 1.5 enums
    public static BorrowPolicy[] values() {
        return new BorrowPolicy[] {NULL, FIFO, LIFO, SOFT_FIFO, SOFT_LIFO};
    }

    // necessary for serialization
    private static int nextOrdinal = 0;
    private final int ordinal = nextOrdinal++;
    private Object readResolve() throws ObjectStreamException {
        return values()[ordinal];
    }
}