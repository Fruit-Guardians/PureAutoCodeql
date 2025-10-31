package com.vmware.vsan.client.services.evacuationstatus.model;

import com.vmware.vise.core.model.data;

@data
public class HostEvacuationCapacityData {
   public String hostName;
   public String iconId;
   public long capacityNeeded;
   public boolean isComponentLimitReached;
   public EvacuationCapacityData preOperationCapacity;
   public EvacuationCapacityData postOperationCapacity;
   public boolean isHostSelected;

   public HostEvacuationCapacityData() {
   }

   public HostEvacuationCapacityData(String hostName) {
      this.hostName = hostName;
      this.preOperationCapacity = new EvacuationCapacityData();
      this.postOperationCapacity = new EvacuationCapacityData();
   }
}
