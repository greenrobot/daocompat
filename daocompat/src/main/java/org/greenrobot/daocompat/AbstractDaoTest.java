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

import org.junit.After;
import org.junit.Before;

import java.io.File;

import io.objectbox.BoxStore;

/**
 * Base implementation to test DAO compat entities.
 *
 * @param <D> DAO class
 * @param <T> Entity type of the DAO
 */
public abstract class AbstractDaoTest<D extends AbstractDao<T, Long>, T> {

    protected File boxStoreDir;
    protected BoxStore store;
    protected D dao;

    @Before
    public void setUp() throws Exception {
        // This works with Android without needing any context
        File tempFile = File.createTempFile("object-store-test", "");
        //noinspection ResultOfMethodCallIgnored
        tempFile.delete();
        boxStoreDir = tempFile;
        store = createBoxStore(boxStoreDir);
        dao = getDao(store);
    }

    @After
    public void tearDown() throws Exception {
        if (store != null) {
            try {
                store.close();
                store.deleteAllFiles();

                File[] files = boxStoreDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        logError("File was not deleted: " + file.getAbsolutePath());
                    }
                }
            } catch (Exception e) {
                logError("Could not clean up test", e);
            }
        }
        if (boxStoreDir != null && boxStoreDir.exists()) {
            File[] files = boxStoreDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    delete(file);
                }
            }
            delete(boxStoreDir);
        }
    }

    private boolean delete(File file) {
        boolean deleted = file.delete();
        if (!deleted) {
            file.deleteOnExit();
            logError("Could not delete " + file.getAbsolutePath());
        }
        return deleted;
    }

    protected void logError(String text) {
        System.err.println(text);
    }

    protected void logError(String text, Exception ex) {
        if (text != null) {
            System.err.println(text);
        }
        ex.printStackTrace();
    }

    protected abstract BoxStore createBoxStore(File boxStoreTempDir);

    protected abstract D getDao(BoxStore store);

    protected abstract Class<T> getDaoClass();

}