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
import org.greenrobot.daocompat.InternalQueryDaoAccess;
import org.greenrobot.daocompat.query.QueryBuilder.Condition;
import io.objectbox.exception.DbException;

/**
 * A repeatable query returning entities.
 *
 * @param <T> The entity class the query will return results for.
 */
abstract class AbstractQuery<T> {
    protected final InternalQueryDaoAccess<T> daoAccess;
    protected final Thread ownerThread;

    protected io.objectbox.query.Query<T> objectBoxQuery;
    protected final Condition[] conditions;

    protected AbstractQuery(AbstractDao<T, ?> dao, io.objectbox.query.Query<T> objectBoxQuery,
                            Condition[] conditions) {
        this.daoAccess = new InternalQueryDaoAccess<>(dao);
        this.objectBoxQuery = objectBoxQuery;
        this.conditions = conditions;
        ownerThread = Thread.currentThread();
    }

    /**
     * Sets the parameter using the (0 based) position in which a query condition was added
     * during building the query.
     * <br/>
     * <b>Note:</b> ObjectBox only supports setting parameters by property type. So if you need to
     * add <b>multiple conditions for the same property, build a new query</b> instead.
     */
    public AbstractQuery<T> setParameter(int index, Object parameter) {
        checkThread();
        checkConditionIndex(index);
        conditions[index].queryCondition.setParameterFor(objectBoxQuery, parameter);
        return this;
    }

    /**
     * Sets the parameters using the (0 based) position in which a query condition was added
     * during building the query.
     */
    public AbstractQuery<T> setParameter(int index, Object parameter1, Object parameter2) {
        checkThread();
        checkConditionIndex(index);
        conditions[index].queryCondition.setParameterFor(objectBoxQuery, parameter1, parameter2);
        return this;
    }

    private void checkConditionIndex(int index) {
        if (index < 0 || index > conditions.length - 1) {
            throw new IllegalArgumentException("Illegal condition index: " + index);
        }
    }

    protected void checkThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new DbException(
                    "Method may be called only in owner thread, use forCurrentThread to get an instance for this thread");
        }
    }

}
