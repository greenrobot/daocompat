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

package org.greenrobot.daocompat.query;

import org.greenrobot.daocompat.AbstractDao;
import org.greenrobot.daocompat.query.QueryBuilder.Condition;
import org.greenrobot.daocompat.query.QueryBuilder.Order;

import java.util.Date;
import java.util.List;

import io.objectbox.Property;
import io.objectbox.exception.DbException;

public class Query<T> extends AbstractQuery<T> {

    private final static class QueryData<T2> extends AbstractQueryData<T2, Query<T2>> {

        QueryData(AbstractDao<T2, ?> dao, Condition[] conditions, Order[] orders, Integer limit,
                  Integer offset) {
            super(dao, conditions, orders, limit, offset);
        }

        @Override
        protected Query<T2> createQuery() {
            return new Query<>(this, dao, buildObjectBoxQuery(), conditions, limit, offset);
        }

    }

    static <T2> Query<T2> create(AbstractDao<T2, ?> dao, Condition[] conditions, Order[] orders,
                                 Integer limit, Integer offset) {
        QueryData<T2> queryData = new QueryData<>(dao, conditions, orders, limit, offset);
        return queryData.forCurrentThread();
    }

    private final QueryData<T> queryData;
    private Integer limit;
    private Integer offset;

    private Query(QueryData<T> queryData, AbstractDao<T, ?> dao, io.objectbox.query.Query<T> query,
                  Condition[] conditions, Integer limit, Integer offset) {
        super(dao, query, conditions);
        this.queryData = queryData;
        this.limit = limit;
        this.offset = offset;
    }

    /**
     * Returns the ObjectBox {@link io.objectbox.query.Query} used internally to allow using
     * methods not available through the daocompat API, like
     * {@link io.objectbox.query.Query#sum(Property) sum(Property)} and
     * {@link io.objectbox.query.Query#remove() remove()}.
     */
    public io.objectbox.query.Query<T> objectBox() {
        checkThread();
        return objectBoxQuery;
    }

    /**
     * Note: all parameters are reset to their initial values specified in {@link QueryBuilder}.
     */
    public Query<T> forCurrentThread() {
        return queryData.forCurrentThread(this);
    }

    /** Executes the query and returns the result as a list containing all entities loaded into memory. */
    public List<T> list() {
        return find();
    }

    /**
     * Executes the query and returns the unique result or null.
     *
     * @return Entity or null if no matching entity was found
     * @throws DbException if the result is not unique
     */
    public T unique() {
        return findUnique();
    }

    public List<T> find() {
        checkThread();
        List<T> list;
        if (limit == null && offset == null) {
            list = objectBoxQuery.find();
        } else {
            if (limit == null || limit < 0) {
                limit = 0;
            }
            if (offset == null || offset < 0) {
                offset = 0;
            }
            list = objectBoxQuery.find(offset, limit);
        }
        return daoAccess.replaceWithEntitiesInScope(list);
    }

    public T findFirst() {
        checkThread();
        return daoAccess.replaceWithEntityInScope(objectBoxQuery.findFirst());
    }

    public T findUnique() {
        checkThread();
        return daoAccess.replaceWithEntityInScope(objectBoxQuery.findUnique());
    }

    /** Returns the count (number of results matching the query). */
    public long count() {
        checkThread();
        return objectBoxQuery.count();
    }

    /**
     * Executes the query and returns the unique result (never null).
     *
     * @return Entity
     * @throws DbException if the result is not unique or no entity was found
     */
    public T uniqueOrThrow() {
        T entity = unique();
        if (entity == null) {
            throw new DbException("No entity found for query");
        }
        return entity;
    }

    /**
     * Remove all entities matching the query.
     */
    public long remove() {
        checkThread();
        return objectBoxQuery.remove();
    }

    @Override
    public Query<T> setParameter(int index, Object parameter) {
        return (Query<T>) super.setParameter(index, parameter);
    }

    public Query<T> setParameter(int index, Date parameter) {
        Long converted = parameter != null ? parameter.getTime() : null;
        return setParameter(index, converted);
    }

    public Query<T> setParameter(int index, Boolean parameter) {
        Integer converted = parameter != null ? (parameter ? 1 : 0) : null;
        return setParameter(index, converted);
    }

    @Override
    public Query<T> setParameter(int index, Object parameter1, Object parameter2) {
        return (Query<T>) super.setParameter(index, parameter1, parameter2);
    }

    /**
     * Sets the limit of the maximum number of results returned by this Query. {@link
     * QueryBuilder#limit(int)} must
     * have been called on the QueryBuilder that created this Query object.
     */
    public void setLimit(int limit) {
        checkThread();
        this.limit = limit;
    }

    /**
     * Sets the offset for results returned by this Query. {@link QueryBuilder#offset(int)} must
     * have been called on
     * the QueryBuilder that created this Query object.
     */
    public void setOffset(int offset) {
        checkThread();
        this.offset = offset;
    }

}
