package com.vmware.vsan.client.services.evacuationstatus.model;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;
import com.vmware.vsan.client.services.diskGroups.data.DecommissionMode;

@data
public class EvacuationTaskData {
   public ManagedObjectReference taskMoRef;
   public String hostName;
   public DecommissionMode decommissionMode;
   public boolean isMaintenanceModeTask;
}
