package com.vmware.vsan.client.services.evacuationstatus.model;

import com.vmware.vise.core.model.data;

@data
public class EvacuationStatusData {
   public boolean isEvacuationStatusSupported;
   public EvacuationEntity[] evacuationEntities;
}
