package org.jeo.android.geogit;

import java.io.File;

import org.geogit.api.DefaultPlatform;

import android.os.Environment;

/**
 * Custom Plaform instance for Android.
 *  
 * @author Justin Deoliveira, Boundless
 */
public class AndroidPlatform extends DefaultPlatform {

    /**
     * On Android there is no user home, so we use the root of the sdcard rather than the system 
     * property "user.home". 
     */
    @Override
    public File getUserHome() {
       
        return Environment.getExternalStorageDirectory();
    }
}
