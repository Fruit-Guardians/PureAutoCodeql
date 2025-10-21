package com.vmware.vsan.client.services.capacity.model;

import com.vmware.vise.core.model.data;

@data
public class UserObjectsCapacityData {
   public long totalUserObjectsUsage;
   public long blockContainerVolumes;
   public long otherFcd;
   public long fileContainerVolumesAttached;
   public long fileContainerVolumesDetached;
   public long nativeFileShares;
   public long iSCSI;
   public long other;

   public String toString() {
      return "totalUserObjectsUsage=" + this.totalUserObjectsUsage + ",\nblockContainerVolumes=" + this.blockContainerVolumes + ",\notherFcd=" + this.otherFcd + ",\nfileContainerVolumesAttached=" + this.fileContainerVolumesAttached + ",\nfileContainerVolumesDetached=" + this.fileContainerVolumesDetached + ",\nnativeFileShares=" + this.nativeFileShares + ",\niSCSI=" + this.iSCSI + ",\nother=" + this.other;
   }
}
