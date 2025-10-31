package com.vmware.vsan.client.services.capacity.model;

import com.vmware.vise.core.model.data;

@data
public class SystemUsageCapacityData {
   public long totalSystemUsage;
   public long performanceMgmtObjects;
   public long fileServiceOverhead;
   public long checksumOverhead;
   public long dedupOverhead;
   public long transientSpace;

   public String toString() {
      return "totalSystemUsage=" + this.totalSystemUsage + ",\nperformanceMgmtObjects=" + this.performanceMgmtObjects + ",\nfileServiceOverhead=" + this.fileServiceOverhead + ",\nchecksumOverhead=" + this.checksumOverhead + ",\ndedupOverhead=" + this.dedupOverhead + ",\ntransientSpace=" + this.transientSpace;
   }
}
