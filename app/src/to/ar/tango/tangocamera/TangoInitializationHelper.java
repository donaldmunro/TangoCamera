/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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

package to.ar.tango.tangocamera;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.util.Log;

import java.io.File;

/**
 * Functions for simplifying the process of initializing TangoService, and function
 * handles loading correct libtango_client_api.so.
 */
public class TangoInitializationHelper
{
   private static final String TAG = "TangoInitHelper";

   public static final int ARCH_ERROR = -2;
   public static final int ARCH_FALLBACK = -1;
   public static final int ARCH_DEFAULT = 0;
   public static final int ARCH_ARM64 = 1;
   public static final int ARCH_ARM32 = 2;
   public static final int ARCH_X86_64 = 3;
   public static final int ARCH_X86 = 4;

   /**
    * Only for apps using the C API:
    * Initializes the underlying TangoService for native apps.
    *
    * @return returns false if the device doesn't have the Tango running as Android Service.
    * Otherwise ture.
    */
   public static final boolean bindTangoService(final Context context,
                                                ServiceConnection connection)
   {
      Intent intent = new Intent();
      intent.setClassName("com.google.tango", "com.google.atap.tango.TangoService");

      boolean hasJavaService = (context.getPackageManager().resolveService(intent, 0) != null);

      // User doesn't have the latest packagename for TangoCore, fallback to the previous name.
      if (! hasJavaService)
      {
         intent = new Intent();
         intent.setClassName("com.projecttango.tango", "com.google.atap.tango.TangoService");
         hasJavaService = (context.getPackageManager().resolveService(intent, 0) != null);
      }

      // User doesn't have a Java-fied TangoCore at all; fallback to the deprecated approach
      // of doing nothing and letting the native side auto-init to the system-service version
      // of Tango.
      if (!hasJavaService)
      {
         Log.w(TAG,
          "Java Tango support not present in project. If the C code does not configure startup correctly it will fail !!!!");
         return false;
      }

      return context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
   }


   private static boolean tryLoad(File lib)
   //---------------------------
   {
      if (! lib.exists())
      {
         Log.e(TAG, lib.getAbsolutePath() + " not found.");
         return false;
      }
      try
      {
         System.load(lib.getAbsolutePath());
         Log.i(TAG, "Success! Using " + lib.getAbsolutePath());
         return true;
      }
      catch (Throwable e)
      {
         Log.e(TAG, lib.getAbsolutePath(), e);
      }
      return false;
   }


   /**
    * Load the libtango_client_api.so library based on different Tango device setup.
    *
    * @return returns the loaded architecture id.
    */
   public static final String loadTangoSharedLibrary() { return loadTangoSharedLibrary(null, "libtango_client_api.so"); }

   static public String[] ABIS = { "default", "arm64-v8a", "armeabi-v7a", "x86_64", "x86"};
   static public File[] DIRS = { new File("/data/data/com.google.tango/libfiles/"),
                                 new File("/data/data/com.projecttango.tango/libfiles/") };

   public static final String loadTangoSharedLibrary(File libBase, String libname)
   //-----------------------------------------------------------------------------
   {
      if (libname == null)
         libname = "libtango_client_api.so";
      File[] dirs;
      if ( (libBase != null) && (libBase.isDirectory()) )
      {
         dirs = new File[DIRS.length + 1];
         dirs[0] = libBase.getAbsoluteFile();
         for (int i=0; i<DIRS.length; i++)
            dirs[i+1] = DIRS[i];
      }
      else
         dirs = DIRS;
      for (String abi : ABIS)
      {
         for (File dir : dirs)
         {
            File f = new File(dir, abi + "/" + libname);
            if (tryLoad(f))
               return abi;
         }
      }

      try
      {
         libname = sysname(libname);
         System.loadLibrary(libname);
         Log.i(TAG, "Falling back to " + libname + " symlink.");
      }
      catch (Throwable e)
      {
         Log.e(TAG, libname + " in system lib", e);
      }
      return null;
   }

   private static String sysname(String libname)
   //--------------------------------------------
   {
      if (libname.startsWith("lib"))
         libname = libname.substring(3);
      if (libname.endsWith(".so"))
         libname = libname.substring(0, libname.length() - 3);
      return libname;
   }
}
