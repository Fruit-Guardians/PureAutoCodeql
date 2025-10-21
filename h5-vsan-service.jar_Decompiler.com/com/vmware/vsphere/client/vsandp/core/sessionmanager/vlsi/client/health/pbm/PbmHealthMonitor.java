package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.health.pbm;

import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.health.IHealthMonitor;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.pbm.PbmConnection;

public class PbmHealthMonitor implements IHealthMonitor<PbmConnection, Object> {
   public void onCreated(PbmConnection resource, Object settings) {
   }

   public void onDisposed(PbmConnection resource, Object settings) {
   }

   public void check(PbmConnection resource, Object settings) throws Exception {
      resource.getProfileManager().fetchResourceType();
   }

   public void onError(PbmConnection resource, Object settings, Throwable t) {
   }
}
