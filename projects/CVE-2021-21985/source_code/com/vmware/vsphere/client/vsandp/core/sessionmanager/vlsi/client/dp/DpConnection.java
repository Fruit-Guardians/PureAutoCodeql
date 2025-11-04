package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.dp;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.InventoryService;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ReplicaSeriesManager;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.VsanDataProtectionRecoverySystem;
import com.vmware.vim.vsandp.binding.vim.vsandp.dps.ServiceInstance;
import com.vmware.vim.vsandp.binding.vim.vsandp.dps.SessionManager;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiConnection;

public class DpConnection extends VlsiConnection {
   public InventoryService getInventoryService() {
      return (InventoryService)this.createStub(InventoryService.class, new ManagedObjectReference("VsanDpClusterInventoryService", "vsan-dp-inventory-service"));
   }

   public ProtectionService getProtectionService() {
      return (ProtectionService)this.createStub(ProtectionService.class, new ManagedObjectReference("VsanDpClusterProtectionService", "vsan-dp-protection-service"));
   }

   public ReplicaSeriesManager getReplicaSeriesManager() {
      return (ReplicaSeriesManager)this.createStub(ReplicaSeriesManager.class, new ManagedObjectReference("VsanDpClusterReplicaSeriesManager", "vsan-dp-replica-series-manager"));
   }

   public VsanDataProtectionRecoverySystem getRecoveryService() {
      return (VsanDataProtectionRecoverySystem)this.createStub(VsanDataProtectionRecoverySystem.class, new ManagedObjectReference("VsanDataProtectionRecoverySystem", "vsan-dp-recovery-system"));
   }

   public SessionManager getSessionManager() {
      return (SessionManager)this.createStub(SessionManager.class, new ManagedObjectReference("VsanDpDpsSessionManager", "vsan-dp-session-manager"));
   }

   public ServiceInstance getServiceInstance() {
      return (ServiceInstance)this.createStub(ServiceInstance.class, new ManagedObjectReference("VsanDpsServiceInstance", "service-instance"));
   }

   public String toString() {
      return this.settings != null ? String.format("DpConnection(host=%s)", this.settings.getHttpSettings().getHost()) : "DpConnection(initializing)";
   }
}
