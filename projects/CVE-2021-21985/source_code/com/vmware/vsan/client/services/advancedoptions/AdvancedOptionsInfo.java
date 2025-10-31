package com.vmware.vsan.client.services.advancedoptions;

import com.vmware.vise.core.model.data;

@data
public class AdvancedOptionsInfo {
   public long objectRepairTimer;
   public boolean isSiteReadLocalityEnabled;
   public boolean isCustomizedSwapObjectEnabled;
   public boolean largeClusterSupportEnabled;
   public boolean isGuestTrimUnmapEnabled;
   public boolean isAutomaticRebalanceEnabled;
   public int rebalancingThreshold;
}
