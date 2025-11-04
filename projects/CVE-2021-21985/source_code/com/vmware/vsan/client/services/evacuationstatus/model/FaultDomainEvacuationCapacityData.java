package com.vmware.vsan.client.services.evacuationstatus.model;

import com.vmware.vise.core.model.data;
import java.util.ArrayList;
import java.util.List;

@data
public class FaultDomainEvacuationCapacityData {
   public String faultDomainName;
   public String message;
   public List<HostEvacuationCapacityData> hostsCapacityData;
   public EvacuationCapacityData preOperationCapacity;
   public EvacuationCapacityData postOperationCapacity;
   public boolean hasInsufficientSpace;
   public boolean isAdditionalHostNeeded;
   public boolean isComponentLimitReached;

   public FaultDomainEvacuationCapacityData() {
   }

   public FaultDomainEvacuationCapacityData(String faultDomainName) {
      this.faultDomainName = faultDomainName;
      this.preOperationCapacity = new EvacuationCapacityData();
      this.postOperationCapacity = new EvacuationCapacityData();
      this.hostsCapacityData = new ArrayList();
   }
}
