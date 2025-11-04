package com.vmware.vsan.client.services.diskGroups.data;

import com.vmware.vise.core.model.data;

@data
public class RemoveDiskGroupSpec {
   public DecommissionMode decommissionMode;
   public VsanDiskMapping[] mappings;
}
