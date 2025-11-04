package com.vmware.vsan.client.services.evacuationstatus.model;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;

@data
public class EvacuationEntity {
   public ManagedObjectReference moRef;
   public String name;
   public String iconId;
   public String uuid;
   public boolean isHostConnected;
   public boolean isInMaintenanceMode;
}
