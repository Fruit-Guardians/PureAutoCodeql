package com.vmware.vsphere.client.vsan.base.service;

import com.vmware.vim.vmomi.client.Client;
import com.vmware.vim.vmomi.core.RequestContext;
import com.vmware.vim.vsan.binding.vsan.version.version6;

public class VsphereHealthServiceFactoryImpl extends VsanServiceFactoryBase<VsphereHealthService> {
   private static final String VSPHERE_HEALTH_SERVICE_SUBDIR = "/analytics/cloudhealth/sdk";

   protected VsphereHealthService create(String vcGuid) {
      ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();

      VsphereHealthServiceImpl var8;
      try {
         Thread.currentThread().setContextClassLoader(VsphereHealthServiceFactoryImpl.class.getClassLoader());
         Class<?> vmodlVersion = version6.class;
         Client client = this.createClient(vcGuid, "/analytics/cloudhealth/sdk", vmodlVersion);
         RequestContext sessionContext = this.prepareSessionContext(vcGuid, client);
         VsphereHealthServiceImpl vsphereHealthService = new VsphereHealthServiceImpl(client, this.getBundleActivator().getVmodlContext().getVmodlTypeMap(), sessionContext);
         var8 = vsphereHealthService;
      } finally {
         Thread.currentThread().setContextClassLoader(oldClassLoader);
      }

      return var8;
   }

   protected void destroy(VsphereHealthService service) {
      service.logout();
   }
}
