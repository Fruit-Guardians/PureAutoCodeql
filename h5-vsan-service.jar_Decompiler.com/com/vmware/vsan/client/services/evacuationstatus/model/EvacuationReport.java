package com.vmware.vsan.client.services.evacuationstatus.model;

import com.vmware.vise.core.model.data;
import com.vmware.vsphere.client.vsan.health.VsanHealthData;
import java.util.Date;

@data
public class EvacuationReport {
   public String status;
   public boolean hasEvacuationReport;
   public Date reportDate;
   public long dataToMove;
   public String[] messages;
   public String[] nonCompliantObjects;
   public String[] inaccessibleObjects;
   public ClusterEvacuationCapacityData clusterCapacity;
   public EvacuationTaskData runningTask;
   public VsanHealthData healthSummary;
}
