package com.vmware.vsphere.client.vsan.base.util;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterHealthSystem;
import com.vmware.vsphere.client.vsan.base.service.VsanServiceFactoryBase;
import com.vmware.vsphere.client.vsan.base.service.VsphereHealthService;

public class VsphereHealthProviderUtils {
   private static VsanServiceFactoryBase<VsphereHealthService> _vsphereHealthServiceFactory;

   public static void setVsphereHealthServiceFactory(VsanServiceFactoryBase<VsphereHealthService> factory) {
      _vsphereHealthServiceFactory = factory;
   }

   public static VsanVcClusterHealthSystem getVsphereHealthSystem(ManagedObjectReference moRef) throws Exception {
      VsphereHealthService vsphereHealthService = (VsphereHealthService)_vsphereHealthServiceFactory.getService(moRef.getServerGuid());
      return vsphereHealthService == null ? null : vsphereHealthService.getVsphereHealthSystem();
   }
}
