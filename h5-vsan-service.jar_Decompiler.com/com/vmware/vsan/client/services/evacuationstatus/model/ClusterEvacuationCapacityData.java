package com.vmware.vsan.client.services.evacuationstatus.model;

import com.vmware.vise.core.model.data;
import java.util.ArrayList;
import java.util.List;

@data
public class ClusterEvacuationCapacityData {
   public EvacuationCapacityData preOperationCapacity = new EvacuationCapacityData();
   public EvacuationCapacityData postOperationCapacity = new EvacuationCapacityData();
   public int warningThreshold;
   public int errorThreshold;
   public List<FaultDomainEvacuationCapacityData> faultDomains = new ArrayList();
   public List<HostEvacuationCapacityData> standaloneHosts = new ArrayList();
   public int faultDomainsNeeded;
}
