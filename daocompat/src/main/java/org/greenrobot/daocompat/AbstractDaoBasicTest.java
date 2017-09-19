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

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Default tests for DAO compat entities.
 *
 * @param <D> DAO class
 * @param <T> Entity type of the DAO
 */
public abstract class AbstractDaoBasicTest<D extends AbstractDao<T, Long>, T> extends AbstractDaoTest<D, T> {

    protected Set<Long> usedPks;
    protected Random random = new Random();

    @Override
    public void setUp() throws Exception {
        super.setUp();
        usedPks = new HashSet<>();
    }

    @Test
    public void testInsertAndLoad() {
        T entity = createEntity();
        dao.insert(entity);
        T entity2 = dao.load(dao.getKey(entity));
        assertNotNull(entity2);
        assertEquals(dao.getKey(entity), dao.getKey(entity2));
    }

    @Test
    public void testInsertInTx() {
        dao.deleteAll();
        List<T> list = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            list.add(createEntityWithRandomPk());
        }
        dao.insertInTx(list);
        assertEquals(list.size(), dao.count());
    }

    @Test
    public void testCount() {
        dao.deleteAll();
        assertEquals(0, dao.count());
        dao.insert(createEntityWithRandomPk());
        assertEquals(1, dao.count());
        dao.insert(createEntityWithRandomPk());
        assertEquals(2, dao.count());
    }

    @Test
    public void testInsertTwice() {
        T entity = createEntity();
        dao.insert(entity);
        dao.insert(entity);
        // inserting twice works fine in objectbox, as insert is mapped to put
    }

    @Test
    public void testInsertOrReplaceTwice() {
        T entity = createEntityWithRandomPk();
        long rowId1 = dao.insert(entity);
        long rowId2 = dao.insertOrReplace(entity);
        if (dao.getPkProperty().type == Long.class) {
            assertEquals(rowId1, rowId2);
        }
    }

    @Test
    public void testInsertOrReplaceInTx() {
        dao.deleteAll();
        List<T> listPartial = new ArrayList<>();
        List<T> listAll = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            T entity = createEntityWithRandomPk();
            if (i % 2 == 0) {
                listPartial.add(entity);
            }
            listAll.add(entity);
        }
        dao.insertOrReplaceInTx(listPartial);
        dao.insertOrReplaceInTx(listAll);
        assertEquals(listAll.size(), dao.count());
    }

    @Test
    public void testDelete() {
        T entity = createEntity();
        dao.insert(entity);
        Long pk = dao.getKey(entity);
        assertNotNull(dao.load(pk));
        dao.deleteByKey(pk);
        assertNull(dao.load(pk));
    }

    @Test
    public void testDeleteAll() {
        List<T> entityList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            T entity = createEntityWithRandomPk();
            entityList.add(entity);
        }
        dao.insertInTx(entityList);
        dao.deleteAll();
        assertEquals(0, dao.count());
        for (T entity : entityList) {
            Long key = dao.getKey(entity);
            assertNotNull(key);
            assertNull(dao.load(key));
        }
    }

    @Test
    public void testDeleteInTx() {
        List<T> entityList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            T entity = createEntityWithRandomPk();
            entityList.add(entity);
        }
        dao.insertInTx(entityList);
        List<T> entitiesToDelete = new ArrayList<>();
        entitiesToDelete.add(entityList.get(0));
        entitiesToDelete.add(entityList.get(3));
        entitiesToDelete.add(entityList.get(4));
        entitiesToDelete.add(entityList.get(8));
        dao.deleteInTx(entitiesToDelete);
        assertEquals(entityList.size() - entitiesToDelete.size(), dao.count());
        for (T deletedEntity : entitiesToDelete) {
            Long key = dao.getKey(deletedEntity);
            assertNotNull(key);
            assertNull(dao.load(key));
        }
    }

    @Test
    public void testDeleteByKeyInTx() {
        List<T> entityList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            T entity = createEntityWithRandomPk();
            entityList.add(entity);
        }
        dao.insertInTx(entityList);
        List<Long> keysToDelete = new ArrayList<>();
        keysToDelete.add(dao.getKey(entityList.get(0)));
        keysToDelete.add(dao.getKey(entityList.get(3)));
        keysToDelete.add(dao.getKey(entityList.get(4)));
        keysToDelete.add(dao.getKey(entityList.get(8)));
        dao.deleteByKeyInTx(keysToDelete);
        assertEquals(entityList.size() - keysToDelete.size(), dao.count());
        for (Long key : keysToDelete) {
            assertNotNull(key);
            assertNull(dao.load(key));
        }
    }

    @Test
    public void testRowId() {
        T entity1 = createEntityWithRandomPk();
        T entity2 = createEntityWithRandomPk();
        long rowId1 = dao.insert(entity1);
        long rowId2 = dao.insert(entity2);
        assertTrue(rowId1 != rowId2);
    }

    @Test
    public void testLoadAll() {
        dao.deleteAll();
        List<T> list = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            T entity = createEntity();
            list.add(entity);
        }
        dao.insertInTx(list);
        List<T> loaded = dao.loadAll();
        assertEquals(list.size(), loaded.size());
    }

    @Test
    public void testQuery() {
        dao.insert(createEntityWithRandomPk());
        T entityForQuery = createEntity();
        dao.insert(entityForQuery);
        dao.insert(createEntityWithRandomPk());

        Long pkForQuery = dao.getKey(entityForQuery);

        List<T> list = dao.queryBuilder().where(
                dao.getPkProperty().eq(pkForQuery),
                dao.getPkProperty().notEq(123)
        ).list();
        assertEquals(1, list.size());
        assertEquals(pkForQuery, dao.getKey(list.get(0)));
    }

    @Test
    public void testUpdate() {
        dao.deleteAll();
        T entity = createEntityWithRandomPk();
        dao.insert(entity);
        dao.update(entity);
        assertEquals(1, dao.count());
    }

    @Test
    public void testSave() {
        if(!checkKeyIsNullable()) {
            return;
        }
        dao.deleteAll();
        T entity = createEntity();
        if (entity != null) {
            dao.save(entity);
            dao.save(entity);
            assertEquals(1, dao.count());
        }
    }

    @Test
    public void testSaveInTx() {
        if(!checkKeyIsNullable()) {
            return;
        }
        dao.deleteAll();
        List<T> listPartial = new ArrayList<>();
        List<T> listAll = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            T entity = createEntity();
            if (i % 2 == 0) {
                listPartial.add(entity);
            }
            listAll.add(entity);
        }
        dao.saveInTx(listPartial);
        dao.saveInTx(listAll);
        assertEquals(listAll.size(), dao.count());
    }

    @Test
    public void testAssignPk() {
        if (dao.isEntityUpdateable()) {
            T entity1 = createEntity();
            if (entity1 != null) {
                T entity2 = createEntity();

                dao.insert(entity1);
                dao.insert(entity2);

                Long pk1 = dao.getKey(entity1);
                assertNotNull(pk1);
                Long pk2 = dao.getKey(entity2);
                assertNotNull(pk2);

                assertFalse(pk1.equals(pk2));

                assertNotNull(dao.load(pk1));
                assertNotNull(dao.load(pk2));
            } else {
                logError("Skipping testAssignPk for " + getDaoClass() + " (createEntity returned null for null key)");
            }
        } else {
            logError("Skipping testAssignPk for not updateable " + getDaoClass());
        }
    }

    protected boolean checkKeyIsNullable() {
        if (createEntity() == null) {
            logError("Test is not available for entities with non-null keys");
            return false;
        }
        return true;
    }

    /** Provides a collision free PK () not returned before in the current test. */
    protected Long nextPk() {
        for (int i = 0; i < 100000; i++) {
            Long pk = createRandomPk();
            if (usedPks.add(pk)) {
                return pk;
            }
        }
        throw new IllegalStateException("Could not find a new PK");
    }

    protected T createEntityWithRandomPk() {
        // objectbox does not support random ids
        return createEntity();
    }

    protected Long createRandomPk() {
        return random.nextLong();
    }

    /**
     * Creates an insertable entity. If the given key is null, but the entity's PK is not null the method must return
     * null.
     */
    protected abstract T createEntity();

}