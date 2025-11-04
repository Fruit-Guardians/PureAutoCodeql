package com.vmware.vsan.client.services.diskGroups.data;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;

@data
public class DiskMappingSpec {
   public ManagedObjectReference clusterRef;
   public VsanDiskMapping[] mappings;
   public boolean isAllFlashSupported;
}
