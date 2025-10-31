package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.health.sso;

import com.vmware.vim.binding.lookup.ServiceInstance;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.health.IHealthMonitor;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.LookupSvcConnection;

public class LookupSvcHealthMonitor implements IHealthMonitor<LookupSvcConnection, Object> {
   public void onCreated(LookupSvcConnection resource, Object settings) {
   }

   public void onDisposed(LookupSvcConnection resource, Object settings) {
   }

   public void check(LookupSvcConnection resource, Object settings) throws Exception {
      ((ServiceInstance)resource.createStub(ServiceInstance.class, "ServiceInstance")).retrieveServiceContent();
   }

   public void onError(LookupSvcConnection resource, Object settings, Throwable t) {
   }
}
