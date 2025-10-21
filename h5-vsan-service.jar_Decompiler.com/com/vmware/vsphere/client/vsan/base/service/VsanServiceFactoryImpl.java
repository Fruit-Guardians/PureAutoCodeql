package com.vmware.vsphere.client.vsan.base.service;

import com.vmware.vim.vmomi.client.Client;
import com.vmware.vim.vmomi.core.RequestContext;

public class VsanServiceFactoryImpl extends VsanServiceFactoryBase<VsanService> implements VsanServiceFactory {
   private static final String VSAN_HEALTH_SERVICE_SUBDIR = "/vsanHealth";

   protected VsanService create(String vcGuid) {
      ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();

      VsanServiceImpl var7;
      try {
         Thread.currentThread().setContextClassLoader(VsanServiceFactoryImpl.class.getClassLoader());
         Client client = this.createClient(vcGuid, "/vsanHealth");
         RequestContext sessionContext = this.prepareSessionContext(vcGuid, client);
         VsanServiceImpl vsanService = new VsanServiceImpl(client, this.getBundleActivator().getVmodlContext().getVmodlTypeMap(), sessionContext, vcGuid);
         var7 = vsanService;
      } finally {
         Thread.currentThread().setContextClassLoader(oldClassLoader);
      }

      return var7;
   }

   protected void destroy(VsanService service) {
      service.logout();
   }
}
