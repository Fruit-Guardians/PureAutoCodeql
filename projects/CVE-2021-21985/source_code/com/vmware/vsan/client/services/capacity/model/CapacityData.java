package com.vmware.vsan.client.services.capacity.model;

import com.vmware.vise.core.model.data;

@data
public class CapacityData {
   public long totalSpace;
   public long freeSpace;
   public long usedSpace;
   public long actuallyWrittenSpace;
   public long overReservedSpace;
   public long vsanOverheadSpace;
   public DedupCapacityData dedupCapacity;
   public VmCapacityData vmCapacity;
   public UserObjectsCapacityData userObjectsCapacity;
   public SystemUsageCapacityData systemUsageCapacity;

   public String toString() {
      return "totalSpace=" + this.totalSpace + ", \nfreeSpace=" + this.freeSpace + ", \nusedSpace=" + this.usedSpace + ", \nactuallyWrittenSpace=" + this.actuallyWrittenSpace + ", \noverReservedSpace=" + this.overReservedSpace + ", \nvsanOverheadSpace=" + this.vsanOverheadSpace + this.dedupCapacity != null ? ", \ndedupCapacity=" + this.dedupCapacity : ", \nvmCapacity=" + this.vmCapacity.toString() + ", \nuserObjectsCapacity=" + this.userObjectsCapacity.toString() + ", \nsystemUsageCapacity=" + this.systemUsageCapacity.toString();
   }
}
