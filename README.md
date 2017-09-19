# DaoCompat: greenDAO on ObjectBox

Using greenDAO? Keep the greenDAO API and use ObjectBox instead of SQLite for an extra boost.
ObjectBox is a superfast object-oriented database with strong relation support.

**DaoCompat = greenDAO - SQLite + ObjectBox**

Using DaoCompat, you can easily switch to ObjectBox.
Of course, this depends on your use of SQL/SQLite features because ObjectBox is a NoSQL database.

Gradle setup
------------
You should start with the basic ObjectBox set up (check the Github page and ).
Additionally, you need to enable the `objectbox.daoCompat` flag via annotation processor options, and add DaoCompat as a dependency.
In the end, your app build.gradle should have those additions: 

    apply plugin: 'io.objectbox' // after applying Android plugin
    
    android {
        defaultConfig {
             javaCompileOptions {
                annotationProcessorOptions {
                    arguments = ['objectbox.daoCompat': 'true']
                }
            }
        }
    }
    
    dependencies {
        compile "io.objectbox:objectbox-daocompat:1.0.0"
    }

First steps
-----------
To create the greenDAO-like DaoSession, you need an instance of BoxStore from ObjectBox.
You typically initialize those objects once for your app, e.g. in `onCreate` in your Application class:

    boxStore = MyObjectBox.builder().androidContext(this).build();
    session = new DaoSession(boxStore);

With the DaoSession you are already in your well-known greenDAO API territory.
For example, you can get DAOs using `session.getMyEntityDao()` and then call methods like `insert(...)` or `load(...)`.  

Links
-----
[greenDAO](http://greenrobot.org/greendao)

[ObjectBox](http://objectbox.io/)

License
-------
    Copyright (C) 2011-2017 Markus Junginger, greenrobot (http://greenrobot.org). All rights reserved.
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
        http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

