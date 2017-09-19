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

import java.util.ArrayList;
import java.util.List;

import io.objectbox.Property;
import org.greenrobot.daocompat.AbstractDao;
import io.objectbox.query.QueryBuilder.StringOrder;
import io.objectbox.query.QueryCondition;

public class QueryBuilder<T> {

    public static class Condition {
        public final QueryCondition queryCondition;
        public final StringOrder stringOrder;
        public Condition(QueryCondition queryCondition, StringOrder stringOrder) {
            this.queryCondition = queryCondition;
            this.stringOrder = stringOrder;
        }
    }

    public static class Order {
        public final Property property;
        public final int orderFlag;
        public Order(Property property, int orderFlag) {
            this.property = property;
            this.orderFlag = orderFlag;
        }
        public Order(Property property) {
            this(property, 0);
        }
    }

    private final AbstractDao<T, ?> dao;

    private final List<Condition> conditions;
    private final List<Order> orders;

    private StringOrder stringOrderCollation;
    private Integer limit;
    private Integer offset;

    /** For internal use by greenDAO only. */
    public static <T2> QueryBuilder<T2> internalCreate(AbstractDao<T2, ?> dao) {
        return new QueryBuilder<>(dao);
    }

    protected QueryBuilder(AbstractDao<T, ?> dao) {
        this.dao = dao;
        this.stringOrderCollation = StringOrder.CASE_INSENSITIVE;
        this.conditions = new ArrayList<>();
        this.orders = new ArrayList<>();
    }

    /**
     * Customizes the ordering of strings used by {@link #orderAsc(Property...)} and {@link #orderDesc(Property...)}.
     * Default is {@link StringOrder#CASE_INSENSITIVE StringOrder#CASE_INSENSITIVE}.
     */
    public QueryBuilder<T> stringOrderCollation(StringOrder stringOrderCollation) {
        this.stringOrderCollation = stringOrderCollation;
        return this;
    }

    /**
     * Adds the given conditions to the where clause using an logical AND. To create new conditions, use the properties
     * given in the generated dao classes.
     */
    public QueryBuilder<T> where(QueryCondition condition, QueryCondition... more) {
        conditions.add(new Condition(condition, stringOrderCollation));
        for (QueryCondition queryCondition : more) {
            conditions.add(new Condition(queryCondition, stringOrderCollation));
        }
        return this;
    }

    /** Adds the given properties to the ORDER BY section using ascending order. */
    public QueryBuilder<T> orderAsc(Property... properties) {
        for (Property property : properties) {
            orders.add(new Order(property));
        }
        return this;
    }

    /** Adds the given properties to the ORDER BY section using descending order. */
    public QueryBuilder<T> orderDesc(Property... properties) {
        for (Property property : properties) {
            orders.add(new Order(property, io.objectbox.query.QueryBuilder.DESCENDING));
        }
        return this;
    }

    /**
     * Limits the number of results returned by queries.
     */
    public QueryBuilder<T> limit(int limit) {
        this.limit = limit;
        return this;
    }

    /**
     * Sets the offset for query results in combination with {@link #limit(int)}. The first {@code limit} results are
     * skipped and the total number of results will be limited by {@code limit}. You cannot use offset without limit.
     */
    public QueryBuilder<T> offset(int offset) {
        this.offset = offset;
        return this;
    }

    /**
     * Builds a reusable query object (Query objects can be executed more efficiently than creating
     * a QueryBuilder for each execution.
     */
    public Query<T> build() {
        Condition[] conditions = this.conditions.toArray(new Condition[0]);
        Order[] orders = this.orders.toArray(new Order[0]);
        return Query.create(dao, conditions, orders, limit, offset);
    }

    /**
     * Shorthand for {@link QueryBuilder#build() build()}.{@link Query#list() list()}; see {@link Query#list()} for
     * details. To execute a query more than once, you should build the query and keep the {@link Query} object for
     * efficiency reasons.
     */
    public List<T> list() {
        return build().list();
    }

    /**
     * Shorthand for {@link QueryBuilder#build() build()}.{@link Query#unique() unique()}; see {@link Query#unique()}
     * for details. To execute a query more than once, you should build the query and keep the {@link Query} object for
     * efficiency reasons.
     */
    public T unique() {
        return build().unique();
    }

    /**
     * Shorthand for {@link QueryBuilder#build() build()}.{@link Query#uniqueOrThrow() uniqueOrThrow()}; see
     * {@link Query#uniqueOrThrow()} for details. To execute a query more than once, you should build the query and
     * keep
     * the {@link Query} object for efficiency reasons.
     */
    public T uniqueOrThrow() {
        return build().uniqueOrThrow();
    }

    /**
     * Shorthand for {@link QueryBuilder#build() build()}.{@link Query#count() count()}; see
     * {@link Query#uniqueOrThrow()} for details. To execute a query more than once, you should build the query and
     * keep
     * the {@link Query} object for efficiency reasons.
     */
    public long count() {
        return build().count();
    }

}
