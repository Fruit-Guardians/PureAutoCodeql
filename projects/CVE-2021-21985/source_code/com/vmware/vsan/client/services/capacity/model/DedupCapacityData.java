package com.vmware.vsan.client.services.capacity.model;

import com.vmware.vise.core.model.data;

@data
public class DedupCapacityData {
   public long dedupSavings;
   public double dedupRatio;
   public long usedSpaceAfterDedup;
   public long usedSpaceBeforeDedup;

   public String toString() {
      return "dedupSavings=" + this.dedupSavings + ", \ndedupRatio=" + this.dedupRatio + ", \nusedSpaceAfterDedup=" + this.usedSpaceAfterDedup + ", \nusedSpaceBeforeDedup=" + this.usedSpaceBeforeDedup;
   }
}
