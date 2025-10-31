package com.vmware.vsan.client.services.stretchedcluster;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;

@data
public class HostStorageConsumptionData {
   public ManagedObjectReference hostRef;
   public long userCapacity = 0L;
   public long reservedCapacity = 0L;
   public long totalCapacity = 0L;
}
