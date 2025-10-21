package com.vmware.vsphere.client.vsan.guardrail;

import com.vmware.vise.core.model.data;
import com.vmware.vsan.client.services.resyncing.data.RepairTimerData;

@data
public class GuardRailResult {
   public String[] hostsInMaintenanceMode;
   public String[] hostsNotConnected;
   public boolean resyncCollected;
   public boolean isClusterInResync;
   public boolean isAutomaticRebalanceSupported;
   public Long objectsToSyncCount;
   public Long recoveryETA;
   public RepairTimerData repairTimerData;
   public boolean hasNetworkPartitioning;
}
