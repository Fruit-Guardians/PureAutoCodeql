package com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util;

import java.util.concurrent.ExecutionException;

public abstract class CheckedRunnable {
   public static void withoutChecked(CheckedRunnable closure) {
      try {
         closure.run();
      } catch (Exception var2) {
         handle(var2);
      }

   }

   public static void handle(Exception e) {
      if (e instanceof ExecutionException && ((ExecutionException)e).getCause() != null) {
         Throwable t = ((ExecutionException)e).getCause();
         if (t instanceof Exception) {
            e = (Exception)t;
         }
      }

      if (e instanceof RuntimeException) {
         throw (RuntimeException)e;
      } else {
         throw new RuntimeException(e);
      }
   }

   public abstract void run() throws Exception;
}
