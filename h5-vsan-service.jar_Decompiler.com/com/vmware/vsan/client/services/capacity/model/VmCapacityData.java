package com.vmware.vsan.client.services.capacity.model;

import com.vmware.vise.core.model.data;

@data
public class VmCapacityData {
   public long totalVmUsage;
   public long vmdkPrimaryUsage;
   public long vmdkPolicyOverheadUsage;
   public long blockContainerPrimaryDataUsage;
   public long blockContainerPolicyOverheadUsage;
   public long swapObjectsUsage;
   public long vmMemorySnapshotUsage;
   public long homeObjectsUsage;
   public long dataProtectionPrimaryUsage;
   public long dataProtectionRaidOverhead;
   public long overReservedSpace;
   public long totalVmCapacity;

   public String toString() {
      return "totalVmUsage=" + this.totalVmUsage + ",\nvmdkPrimaryUsage=" + this.vmdkPrimaryUsage + ",\nvmdkPolicyOverheadUsage=" + this.vmdkPolicyOverheadUsage + ",\nblockContainerPrimaryDataUsage=" + this.blockContainerPrimaryDataUsage + ",\nblockContainerPolicyOverheadUsage=" + this.blockContainerPolicyOverheadUsage + ",\nswapObjectsUsage=" + this.swapObjectsUsage + ",\nvmMemorySnapshotUsage=" + this.vmMemorySnapshotUsage + ",\nhomeObjectsUsage=" + this.homeObjectsUsage + ",\ndataProtectionPrimaryUsage=" + this.dataProtectionPrimaryUsage + ",\ndataProtectionRaidOverhead=" + this.dataProtectionRaidOverhead + ",\noverReservedSpace=" + this.overReservedSpace + ",\ntotalVmCapacity=" + this.totalVmCapacity;
   }
}
