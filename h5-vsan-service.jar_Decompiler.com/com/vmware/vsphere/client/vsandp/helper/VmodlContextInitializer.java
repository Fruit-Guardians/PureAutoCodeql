package com.vmware.vsphere.client.vsandp.helper;

import com.vmware.vim.vmomi.core.types.VmodlContext;

public final class VmodlContextInitializer {
   public static VmodlContext createContext(String[] vmodls) {
      ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
      VmodlContext vmodlContext = null;

      try {
         Thread.currentThread().setContextClassLoader(VmodlContextInitializer.class.getClassLoader());
         vmodlContext = VmodlContext.createContext(vmodls);
      } finally {
         Thread.currentThread().setContextClassLoader(originalClassLoader);
      }

      return vmodlContext;
   }
}
