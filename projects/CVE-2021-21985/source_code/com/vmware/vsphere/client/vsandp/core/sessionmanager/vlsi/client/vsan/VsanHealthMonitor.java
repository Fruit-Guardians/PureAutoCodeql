package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vsan;

import com.vmware.vim.binding.vim.fault.VsanFault;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.health.IHealthMonitor;

public class VsanHealthMonitor implements IHealthMonitor<VsanConnection, Object> {
   public void check(VsanConnection resource, Object settings) {
      try {
         resource.getVsanCapabilitySystem().getCapabilities((ManagedObjectReference[])null);
      } catch (VsanFault var4) {
         throw new RuntimeException(var4);
      }
   }

   public void onCreated(VsanConnection resource, Object settings) {
   }

   public void onDisposed(VsanConnection resource, Object settings) {
   }

   public void onError(VsanConnection resource, Object settings, Throwable t) {
   }
}
