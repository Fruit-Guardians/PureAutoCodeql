package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.dp;

import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.health.IHealthMonitor;

public class DpHealthMonitor implements IHealthMonitor<DpConnection, Object> {
   public void check(DpConnection resource, Object settings) {
      resource.getServiceInstance().ping();
   }

   public void onCreated(DpConnection resource, Object settings) {
   }

   public void onDisposed(DpConnection resource, Object settings) {
   }

   public void onError(DpConnection resource, Object settings, Throwable t) {
   }
}
