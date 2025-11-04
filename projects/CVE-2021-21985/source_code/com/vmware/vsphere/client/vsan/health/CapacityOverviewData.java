package com.vmware.vsphere.client.vsan.health;

import com.vmware.vise.core.model.data;

@data
public class CapacityOverviewData {
   public long provisionedSpace;
   public long usedSpace;
   public long totalSpace;
   public long reservedSpace;
   public long physicalUsedSpace;
   public long overReservedSpace;
   public long freeSpace;
   public long vsanOverheadSpace;
   public long vsanDpOverheadSpace;
}
