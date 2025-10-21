package com.vmware.vsphere.client.vsan.base.service;

import com.vmware.vim.vmomi.core.types.VmodlContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class VsanServiceBundleActivator implements ServiceBundleActivator {
   private static final Log _logger = LogFactory.getLog(VsanServiceBundleActivator.class);
   private VmodlContext _vsanVmodlContext;

   public VmodlContext getVmodlContext() {
      return this._vsanVmodlContext;
   }

   public VsanServiceBundleActivator() {
      ClassLoader bundleClassLoader = VsanServiceBundleActivator.class.getClassLoader();
      ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();

      try {
         _logger.debug("Loading VSAN vmodl context.");
         Thread.currentThread().setContextClassLoader(bundleClassLoader);
         this._vsanVmodlContext = VmodlContext.createContext(new String[]{"com.vmware.vim.binding.vim", "com.vmware.vim.vsan.binding.vim", "com.vmware.vim.vsandp.binding.vim.vsandp"});
         _logger.debug("Successfully loaded VSAN vmodl context.");
      } finally {
         Thread.currentThread().setContextClassLoader(currentClassLoader);
      }

   }
}
