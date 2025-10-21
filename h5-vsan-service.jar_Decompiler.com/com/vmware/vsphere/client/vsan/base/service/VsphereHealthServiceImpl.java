package com.vmware.vsphere.client.vsan.base.service;

import com.vmware.vim.binding.vmodl.ManagedObject;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vmomi.client.Client;
import com.vmware.vim.vmomi.core.RequestContext;
import com.vmware.vim.vmomi.core.Stub;
import com.vmware.vim.vmomi.core.types.VmodlType;
import com.vmware.vim.vmomi.core.types.VmodlTypeMap;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterHealthSystem;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class VsphereHealthServiceImpl implements VsphereHealthService {
   private static final Log _logger = LogFactory.getLog(VsphereHealthServiceImpl.class);
   private static final ManagedObjectReference VSPHERE_HEALTH_SYSTEM_MO_REF = new ManagedObjectReference("VsanVcClusterHealthSystem", "cloud-health", (String)null);
   private final Client _vmomiClient;
   private final VmodlTypeMap _vmodlTypeMap;
   private final RequestContext _sessionContext;
   private VsanVcClusterHealthSystem _vsanVcClusterHealthSystem;

   public VsphereHealthServiceImpl(Client vmomiClient, VmodlTypeMap vmodlTypeMap, RequestContext sessionContext) {
      this._vmomiClient = vmomiClient;
      this._vmodlTypeMap = vmodlTypeMap;
      this._sessionContext = sessionContext;
   }

   public VsanVcClusterHealthSystem getVsphereHealthSystem() {
      if (this._vsanVcClusterHealthSystem == null) {
         this._vsanVcClusterHealthSystem = (VsanVcClusterHealthSystem)this.createManagedObject(VSPHERE_HEALTH_SYSTEM_MO_REF);
      }

      return this._vsanVcClusterHealthSystem;
   }

   public void logout() {
      try {
         if (this._vmomiClient != null) {
            this._vmomiClient.shutdown();
         }
      } catch (Exception var2) {
         _logger.error("Failed to shutdown vlsi client: " + var2.getMessage());
      }

   }

   private <T extends ManagedObject> T createManagedObject(ManagedObjectReference moRef) {
      ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();

      ManagedObject var7;
      try {
         Thread.currentThread().setContextClassLoader(VsphereHealthServiceImpl.class.getClassLoader());
         VmodlType vmodlType = this._vmodlTypeMap.getVmodlType(moRef.getType());
         Class<T> typeClass = vmodlType.getTypeClass();
         T result = this._vmomiClient.createStub(typeClass, moRef);
         ((Stub)result)._setRequestContext(this._sessionContext);
         var7 = result;
      } finally {
         Thread.currentThread().setContextClassLoader(oldClassLoader);
      }

      return var7;
   }
}
