package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.health.vc;

import com.vmware.vim.binding.vim.ServiceInstance;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.health.IHealthMonitor;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;

public class VcHealthMonitor implements IHealthMonitor<VcConnection, Object> {
   public static final String SERVICE_INSTANCE = "ServiceInstance";

   public void onCreated(VcConnection resource, Object settings) {
   }

   public void onDisposed(VcConnection resource, Object settings) {
   }

   public void check(VcConnection resource, Object settings) {
      ServiceInstance si = (ServiceInstance)resource.createStub(ServiceInstance.class, "ServiceInstance");
      si.getServerClock();
   }

   public void onError(VcConnection resource, Object settings, Throwable t) {
   }
}
