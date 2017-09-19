/*
 * Copyright (C) 2011-2017 Markus Junginger, greenrobot (http://greenrobot.org)
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

package org.greenrobot.daocompat;

import io.objectbox.BoxStore;
import io.objectbox.annotation.apihint.Experimental;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import io.objectbox.exception.DbException;
import org.greenrobot.daocompat.rx.RxTransaction;
import rx.schedulers.Schedulers;

/**
 * DaoSession gives you access to your DAOs, offers convenient persistence methods, and also serves as a session cache.<br>
 * <br>
 * To access the DAOs, call the get{entity}Dao methods by the generated DaoSession sub class.<br>
 * <br>
 * DaoSession offers many of the available persistence operations on entities as a convenience. Consider using DAOs
 * directly to access all available operations, especially if you call a lot of operations on a single entity type to
 * avoid the overhead imposed by DaoSession (the overhead is small, but it may add up).<br>
 * <br>
 * By default, the DaoSession has a session cache (IdentityScopeType.Session). The session cache is not just a plain
 * data cache to improve performance, but also manages object identities. For example, if you load the same entity twice
 * in a query, you will get a single Java object instead of two when using a session cache. This is particular useful
 * for relations pointing to a common set of entities.
 *
 * This class is thread-safe.
 *
 * @author Markus
 *
 */
public class AbstractDaoSession {
    private final Map<Class<?>, AbstractDao<?, ?>> entityToDao;
    private final BoxStore store;

    private volatile RxTransaction rxTxPlain;
    private volatile RxTransaction rxTxIo;

    public AbstractDaoSession(BoxStore store) {
        this.store = store;
        this.entityToDao = new HashMap<>();
    }

    protected <T> void registerDao(Class<T> entityClass, AbstractDao<T, ?> dao) {
        entityToDao.put(entityClass, dao);
    }

    /** Convenient call for {@link AbstractDao#insert(Object)}. */
    public <T> long insert(T entity) {
        @SuppressWarnings("unchecked")
        AbstractDao<T, ?> dao = (AbstractDao<T, ?>) getDao(entity.getClass());
        return dao.insert(entity);
    }

    /** Convenient call for {@link AbstractDao#insertOrReplace(Object)}. */
    public <T> long insertOrReplace(T entity) {
        @SuppressWarnings("unchecked")
        AbstractDao<T, ?> dao = (AbstractDao<T, ?>) getDao(entity.getClass());
        return dao.insertOrReplace(entity);
    }

    /** Convenient call for {@link AbstractDao#refresh(Object)}. */
    public <T> void refresh(T entity) {
        @SuppressWarnings("unchecked")
        AbstractDao<T, ?> dao = (AbstractDao<T, ?>) getDao(entity.getClass());
        dao.refresh(entity);
    }

    /** Convenient call for {@link AbstractDao#update(Object)}. */
    public <T> void update(T entity) {
        @SuppressWarnings("unchecked")
        AbstractDao<T, ?> dao = (AbstractDao<T, ?>) getDao(entity.getClass());
        dao.update(entity);
    }

    /** Convenient call for {@link AbstractDao#delete(Object)}. */
    public <T> void delete(T entity) {
        @SuppressWarnings("unchecked")
        AbstractDao<T, ?> dao = (AbstractDao<T, ?>) getDao(entity.getClass());
        dao.delete(entity);
    }

    /** Convenient call for {@link AbstractDao#deleteAll()}. */
    public <T> void deleteAll(Class<T> entityClass) {
        @SuppressWarnings("unchecked")
        AbstractDao<T, ?> dao = (AbstractDao<T, ?>) getDao(entityClass);
        dao.deleteAll();
    }

    /** Convenient call for {@link AbstractDao#load(Long)}. */
    public <T, K> T load(Class<T> entityClass, Long key) {
        @SuppressWarnings("unchecked")
        AbstractDao<T, K> dao = (AbstractDao<T, K>) getDao(entityClass);
        return dao.load(key);
    }

    /** Convenient call for {@link AbstractDao#loadAll()}. */
    public <T, K> List<T> loadAll(Class<T> entityClass) {
        @SuppressWarnings("unchecked")
        AbstractDao<T, K> dao = (AbstractDao<T, K>) getDao(entityClass);
        return dao.loadAll();
    }

//    /** Convenient call for {@link AbstractDao#queryBuilder()}. */
//    public <T> QueryBuilder<T> queryBuilder(Class<T> entityClass) {
//        @SuppressWarnings("unchecked")
//        AbstractDao<T, ?> dao = (AbstractDao<T, ?>) getDao(entityClass);
//        return dao.queryBuilder();
//    }

    public AbstractDao<?, ?> getDao(Class<? extends Object> entityClass) {
        AbstractDao<?, ?> dao = entityToDao.get(entityClass);
        if (dao == null) {
            throw new DbException("No DAO registered for " + entityClass);
        }
        return dao;
    }

    /**
     * Run the given Runnable inside a database transaction. If you except a result, consider callInTx.
     */
    public void runInTx(Runnable runnable) {
        store.runInTx(runnable);
    }

    /**
     * Calls the given Callable inside a database transaction and returns the result of the Callable. If you don't
     * except a result, consider runInTx.
     */
    public <V> V callInTx(Callable<V> callable) throws Exception {
        return store.callInTx(callable);
    }

    /**
     * Like {@link #callInTx(Callable)} but does not require Exception handling (rethrows an Exception as a runtime
     * DaoException).
     */
    public <V> V callInTxNoException(Callable<V> callable) {
        V result;
        try {
            result = callable.call();
        } catch (Exception e) {
            throw new DbException("Callable failed: " + e.getMessage());
        }
        return result;
    }

    /** Allows to inspect the meta model using DAOs (e.g. querying table names or properties). */
    public Collection<AbstractDao<?, ?>> getAllDaos() {
        return Collections.unmodifiableCollection(entityToDao.values());
    }

//    /**
//     * Creates a new {@link AsyncSession} to issue asynchronous entity operations. See {@link AsyncSession} for details.
//     */
//    public AsyncSession startAsyncSession() {
//        // TODO ut: possibly add support with objectbox
////        return new AsyncSession(this);
//        throw new UnsupportedOperationException("Not supported with objectbox.");
//    }

    /**
     * The returned {@link RxTransaction} allows DB transactions using Rx Observables without any Scheduler set for
     * subscribeOn.
     *
     * @see #rxTx()
     */
    @Experimental
    public RxTransaction rxTxPlain() {
        if (rxTxPlain == null) {
            rxTxPlain = new RxTransaction(this);
        }
        return rxTxPlain;
    }

    /**
     * The returned {@link RxTransaction} allows DB transactions using Rx Observables using RX's IO scheduler for
     * subscribeOn.
     *
     * @see #rxTxPlain()
     */
    @Experimental
    public RxTransaction rxTx() {
        if (rxTxIo == null) {
            rxTxIo = new RxTransaction(this, Schedulers.io());
        }
        return rxTxIo;
    }

}
