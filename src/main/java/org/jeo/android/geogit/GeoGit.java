/* Copyright 2014 The jeo project. All rights reserved.
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
package org.jeo.android.geogit;

import org.geogit.api.Platform;
import org.geogit.di.GeogitModule;
import org.geogit.storage.sqlite.AndroidSQLiteModule;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;

/**
 * Android specific GeoGit driver.
 * 
 * @author Justin Deoliveira, Boundless
 */
public class GeoGit extends org.jeo.geogit.GeoGit {

    @Override
    protected Injector createGeoGITInjector() {
        return Guice.createInjector(Modules.override(new GeogitModule()).with(new AbstractModule(){
            @Override
            protected void configure() {
                bind(Platform.class).to(AndroidPlatform.class).asEagerSingleton();
            }
        }, new AndroidSQLiteModule()));
        
    }
}
