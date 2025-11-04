package com.vmware.vsan.client.services.diskGroups.data;

import com.vmware.vise.core.model.data;

@data
public class RecreateDiskGroupSpec {
   public VsanDiskMapping mapping;
   public DecommissionMode decommissionMode;
}
