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

import org.greenrobot.daocompat.identityscope.IdentityScopeLong;
import org.greenrobot.daocompat.query.QueryBuilder;
import org.greenrobot.daocompat.rx.RxDao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import io.objectbox.Box;
import io.objectbox.EntityInfo;
import io.objectbox.Property;
import io.objectbox.annotation.apihint.Experimental;
import io.objectbox.exception.DbException;
import rx.schedulers.Schedulers;

/**
 * A DAO compat API layer to make the transition from greenDAO to ObjectBox easier.
 * <p>
 * Note: ObjectBox does not need to know if an entity is inserted, updated, replaced, etc. ObjectBox just puts objects.
 * Usually, the put semantics are just fine,but you may still want to review each use case and change to
 * {@link #put(Object)} once verified.
 *
 * @param <T> Entity class
 * @param <K> Always Long with ObjectBox
 */
public abstract class AbstractDao<T, K> {

    protected Box<T> box;
    private EntityInfo entityInfo;

    protected final IdentityScopeLong<T> identityScope;

    protected final AbstractDaoSession session;

    private volatile RxDao<T, K> rxDao;
    private volatile RxDao<T, K> rxDaoPlain;

    public AbstractDao(Box<T> box, EntityInfo entityInfo, IdentityScopeLong<T> identityScope) {
        this(null, box, entityInfo, identityScope);
    }

    public AbstractDao(AbstractDaoSession daoSession, Box<T> box, EntityInfo entityInfo,
                       IdentityScopeLong<T> identityScope) {
        this.session = daoSession;
        this.box = box;
        this.entityInfo = entityInfo;
        this.identityScope = identityScope;
    }

    /** Get the "native" ObjectBox API. */
    public Box<T> getBox() {
        return box;
    }

    public AbstractDaoSession getSession() {
        return session;
    }

    public String getTablename() {
        return entityInfo.getDbName();
    }

    public Property[] getEntityInfo() {
        return entityInfo.getAllProperties();
    }

    public Property getPkProperty() {
        return entityInfo.getIdProperty();
    }

    /**
     * Loads the entity for the given PK.
     *
     * @param key a PK value
     * @return The entity or null, if no entity matched the PK value
     */
    public T load(Long key) {
        if (key == null) {
            return null;
        }
        if (identityScope != null) {
            T entity = identityScope.get(key);
            if (entity != null) {
                return entity;
            }
        }
        T entity = box.get(key);
        attachEntity(entity);
        if (identityScope != null) {
            identityScope.put(key, entity);
        }
        return entity;
    }

    /** Loads all available entities from the database. */
    public List<T> loadAll() {
        List<T> entities = box.getAll();
        return replaceWithEntitiesInScope(entities);
    }

    /** Internal use only. */
    protected final List<T> replaceWithEntitiesInScope(List<T> entities) {
        int count = entities.size();
        if (identityScope == null || count == 0) {
            for (T entity : entities) {
                attachEntity(entity);
            }
            return entities;
        } else {
            List<T> list = new ArrayList<>(count);

            identityScope.lock();
            identityScope.reserveRoom(count);

            try {
                // prefer instances from scope
                for (T entity : entities) {
                    Long key = getKeyVerified(entity);
                    T entityInScope = identityScope.getNoLock(key);
                    if (entityInScope != null) {
                        list.add(entityInScope);
                    } else {
                        attachEntity(entity);
                        identityScope.putNoLock(key, entity);
                        list.add(entity);
                    }
                }
            } finally {
                identityScope.unlock();
            }

            return list;
        }
    }

    /** Internal use only. */
    protected final T replaceWithEntityInScope(T entity) {
        if (entity != null && identityScope != null) {
            T entityInScope = identityScope.get(getKeyVerified(entity));
            if (entityInScope != null) {
                return entityInScope;
            }
        }
        return entity;
    }

    /** Detaches an entity from the identity scope (session). Subsequent query results won't return this object. */
    public boolean detach(T entity) {
        if (identityScope != null) {
            Long key = getKeyVerified(entity);
            return identityScope.detach(key, entity);
        } else {
            return false;
        }
    }

    /**
     * Detaches all entities (of type T) from the identity scope (session). Subsequent query results won't return any
     * previously loaded objects.
     */
    public void detachAll() {
        if (identityScope != null) {
            identityScope.clear();
        }
    }

    /**
     * Objectbox does not differentiate between insert and update, it just uses put. See {@link Box#put(Object)}.
     */
    public long put(T entity) {
        return performPut(entity, true);
    }

    private long performPut(T entity, boolean setKeyAndAttach) {
        long key = box.put(entity);
        if (setKeyAndAttach) {
            attachEntity(key, entity, true);
        }
        return key;
    }

    /**
     * Objectbox does not differentiate between insert and update, it just uses put. See {@link Box#put(Object[])}.
     */
    public void put(final T... entities) {
        if (identityScope != null) {
            identityScope.lock();
        }
        box.getStore().runInTx(new Runnable() {
            @Override
            public void run() {
                for (T entity : entities) {
                    // put one-by-one to attach one-by-one to identity scope
                    put(entity);
                }
            }
        });
        if (identityScope != null) {
            identityScope.unlock();
        }
    }

    /**
     * Objectbox does not differentiate between insert and update, it just uses put. See {@link Box#put(Collection)}.
     */
    public void put(final Collection<T> entities) {
        if (identityScope != null) {
            identityScope.lock();
        }
        box.getStore().runInTx(new Runnable() {
            @Override
            public void run() {
                for (T entity : entities) {
                    // put one-by-one to attach one-by-one to identity scope
                    put(entity);
                }
            }
        });
        if (identityScope != null) {
            identityScope.unlock();
        }
    }

    /**
     * NOTE: ObjectBox always uses {@link Box#put(Collection)} semantics, which might differ in some situations.
     * <p>
     * Inserts the given entities in the database using a transaction.
     *
     * @param entities The entities to insert.
     */
    public void insertInTx(Collection<T> entities) {
        put(entities);
    }

    /**
     * NOTE: ObjectBox always uses {@link Box#put(Object[])} semantics, which might differ in some situations.
     * <p>
     * Inserts the given entities in the database using a transaction.
     *
     * @param entities The entities to insert.
     */
    public void insertInTx(T... entities) {
        put(entities);
    }

    /**
     * NOTE: ObjectBox always uses {@link Box#put(Collection)} semantics, which might differ in some situations.
     * <p>
     * Inserts the given entities in the database using a transaction. The given entities will become tracked if the PK
     * is set.
     *
     * @param entities      The entities to insert.
     * @param setPrimaryKey if true, the PKs of the given will be set after the insert; pass false to improve
     *                      performance.
     */
    public void insertInTx(Collection<T> entities, boolean setPrimaryKey) {
        put(entities);
    }

    /**
     * NOTE: ObjectBox always uses {@link Box#put(Collection)} semantics, which might differ in some situations.
     * <p>
     * NOTE 2: ObjectBox always assigns an ID.
     * <p>
     * Inserts or replaces the given entities in the database using a transaction. The given entities will become
     * tracked if the PK is set.
     *
     * @param entities      The entities to insert.
     * @param setPrimaryKey if true, the PKs of the given will be set after the insert; pass false to improve
     *                      performance.
     */
    public void insertOrReplaceInTx(Collection<T> entities, boolean setPrimaryKey) {
        put(entities);
    }

    /**
     * NOTE: ObjectBox always uses {@link Box#put(Collection)} semantics, which might differ in some situations.
     * <p>
     * Inserts or replaces the given entities in the database using a transaction.
     *
     * @param entities The entities to insert.
     */
    public void insertOrReplaceInTx(Collection<T> entities) {
        put(entities);
    }

    /**
     * NOTE: ObjectBox always uses {@link Box#put(Object[])} semantics, which might differ in some situations.
     * <p>
     * Inserts or replaces the given entities in the database using a transaction.
     *
     * @param entities The entities to insert.
     */
    public void insertOrReplaceInTx(T... entities) {
        put(entities);
    }

    /**
     * NOTE: ObjectBox always uses {@link Box#put(Object)} semantics, which might differ in some situations.
     * <p>
     * Insert an entity into the table associated with a concrete DAO.
     *
     * @return row ID of newly inserted entity
     */
    public long insert(T entity) {
        return put(entity);
    }

    /**
     * NOTE: ObjectBox always uses {@link Box#put(Object)} semantics, which might differ in some situations.
     * NOTE 2: ObjectBox always assigns an ID.
     * Warning: This may be faster, but the entity should not be used anymore. The entity also won't be attached to
     * identity scope.
     *
     * @return row ID of newly inserted entity
     */
    public long insertWithoutSettingPk(T entity) {
        return performPut(entity, false);
    }

    /**
     * NOTE: ObjectBox always uses {@link Box#put(Object)} semantics, which might differ in some situations.
     * <p>
     * Insert an entity into the table associated with a concrete DAO.
     *
     * @return row ID of newly inserted entity
     */
    public long insertOrReplace(T entity) {
        return put(entity);
    }

    /**
     * NOTE: ObjectBox always uses {@link Box#put(Object)} semantics, which might differ in some situations.
     * <p>
     * "Saves" an entity to the database: depending on the existence of the key property, it will be inserted
     * (key is null) or updated (key is not null).
     * <p>
     * This is similar to {@link #insertOrReplace(Object)}, but may be more efficient, because if a key is present,
     * it does not have to query if that key already exists.
     */
    public void save(T entity) {
        put(entity);
    }

    /**
     * NOTE: ObjectBox always uses {@link Box#put(Object[])} semantics, which might differ in some situations.
     * <p>
     * Saves (see {@link #save(Object)}) the given entities in the database using a transaction.
     *
     * @param entities The entities to save.
     */
    public void saveInTx(T... entities) {
        put(entities);
    }

    /**
     * NOTE: ObjectBox always uses {@link Box#put(Collection)} semantics, which might differ in some situations.
     * <p>
     * Saves (see {@link #save(Object)}) the given entities in the database using a transaction.
     *
     * @param entities The entities to save.
     */
    public void saveInTx(Collection<T> entities) {
        put(entities);
    }

    public void deleteAll() {
        box.removeAll();
        if (identityScope != null) {
            identityScope.clear();
        }
    }

    /** Deletes the given entity from the database. Currently, only single value PK entities are supported. */
    public void delete(T entity) {
        box.remove(entity);
    }

    /** Deletes an entity with the given PK from the database. Currently, only single value PK entities are supported. */
    public void deleteByKey(Long key) {
        box.remove(key);
        if (identityScope != null) {
            identityScope.remove(key);
        }
    }

    /**
     * Deletes the given entities in the database using a transaction.
     *
     * @param entities The entities to delete.
     */
    public void deleteInTx(Collection<T> entities) {
        List<Long> keysToRemoveFromIdentityScope = null;
        if (identityScope != null) {
            identityScope.lock();
            keysToRemoveFromIdentityScope = new ArrayList<>();
        }
        try {
            for (T entity : entities) {
                Long key = getKeyVerified(entity);
                if (keysToRemoveFromIdentityScope != null) {
                    keysToRemoveFromIdentityScope.add(key);
                }
            }
            box.remove(entities);
        } finally {
            if (identityScope != null) {
                identityScope.unlock();
            }
        }
        if (keysToRemoveFromIdentityScope != null) {
            identityScope.remove(keysToRemoveFromIdentityScope);
        }
    }

    /**
     * Deletes the given entities in the database using a transaction.
     *
     * @param entities The entities to delete.
     */
    public void deleteInTx(T... entities) {
        List<Long> keysToRemoveFromIdentityScope = null;
        if (identityScope != null) {
            identityScope.lock();
            keysToRemoveFromIdentityScope = new ArrayList<>();
        }
        try {
            for (T entity : entities) {
                Long key = getKeyVerified(entity);
                if (keysToRemoveFromIdentityScope != null) {
                    keysToRemoveFromIdentityScope.add(key);
                }
            }
            box.remove(entities);
        } finally {
            if (identityScope != null) {
                identityScope.unlock();
            }
        }
        if (keysToRemoveFromIdentityScope != null) {
            identityScope.remove(keysToRemoveFromIdentityScope);
        }
    }

    /**
     * Deletes all entities with the given keys in the database using a transaction.
     *
     * @param keys Keys of the entities to delete.
     */
    public void deleteByKeyInTx(Collection<Long> keys) {
        if (identityScope != null) {
            identityScope.lock();
        }
        box.removeByKeys(keys);
        if (identityScope != null) {
            identityScope.unlock();
            identityScope.remove(keys);
        }
    }

    /**
     * Deletes all entities with the given keys in the database using a transaction.
     *
     * @param keys Keys of the entities to delete.
     */
    public void deleteByKeyInTx(Long... keys) {
        // objectbox wants a list when using the Long wrapper
        deleteByKeyInTx(Arrays.asList(keys));
    }

    /**
     * Resets all locally changed properties of the entity by reloading the values from the database.
     */
    public void refresh(T entity) {
        Long key = getKeyVerified(entity);

        T loadedEntity = box.get(key);
        if (loadedEntity == null) {
            throw new DbException("Entity does not exist in the database anymore: " + entity.getClass() + " with key " + key);
        }
        readEntity(loadedEntity, entity);
        attachEntity(key, entity, true);
    }

    public void update(T entity) {
        put(entity);
    }

    public QueryBuilder<T> queryBuilder() {
        return QueryBuilder.internalCreate(this);
    }

    /**
     * Attaches the entity to the identity scope. Calls attachEntity(T entity).
     *
     * @param key    Needed only for identity scope, pass null if there's none.
     * @param entity The entitiy to attach
     */
    protected final void attachEntity(Long key, T entity, boolean lock) {
        attachEntity(entity);
        if (identityScope != null && key != null) {
            if (lock) {
                identityScope.put(key, entity);
            } else {
                identityScope.putNoLock(key, entity);
            }
        }
    }

    /**
     * Sub classes with relations additionally set the DaoMaster here. Must be called before the entity is attached to
     * the identity scope.
     *
     * @param entity The entitiy to attach
     */
    protected void attachEntity(T entity) {
    }

    /**
     * Updates the given entities in the database using a transaction.
     *
     * @param entities The entities to insert.
     */
    public void updateInTx(Collection<T> entities) {
        put(entities);
    }

    /**
     * Updates the given entities in the database using a transaction.
     *
     * @param entities The entities to update.
     */
    public void updateInTx(T... entities) {
        put(entities);
    }

    public long count() {
        return box.count();
    }

    /** See {@link #getKey(Object)}, but guarantees that the returned key is never null (throws if null). */
    protected Long getKeyVerified(T entity) {
        Long key = getKey(entity);
        if (key == null) {
            if (entity == null) {
                throw new NullPointerException("Entity may not be null");
            } else {
                throw new DbException("Entity has no key");
            }
        } else {
            return key;
        }
    }

    /**
     * The returned RxDao is a special DAO that let's you interact with Rx Observables without any Scheduler set
     * for subscribeOn.
     *
     * @see #rx()
     */
    @Experimental
    public RxDao<T, K> rxPlain() {
        if (rxDaoPlain == null) {
            rxDaoPlain = new RxDao<>(this);
        }
        return rxDaoPlain;
    }

    /**
     * The returned RxDao is a special DAO that let's you interact with Rx Observables using RX's IO scheduler for
     * subscribeOn.
     *
     * @see #rxPlain()
     */
    @Experimental
    public RxDao<T, K> rx() {
        if (rxDao == null) {
            rxDao = new RxDao<>(this, Schedulers.io());
        }
        return rxDao;
    }


    /**
     * Reads the values from the given entity into an existing entity.
     */
    abstract protected void readEntity(T from, T to);

    /**
     * Returns the value of the primary key, if the entity has a single primary key, or, if not, null. Returns null if
     * entity is null.
     */
    abstract protected Long getKey(T entity);

    /** Returns true if the Entity class can be updated, e.g. for setting the PK after insert. */
    abstract protected boolean isEntityUpdateable();

}
