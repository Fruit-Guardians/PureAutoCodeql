package com.vmware.vsan.client.services.diskGroups.data;

import com.vmware.vim.binding.vim.host.ScsiDisk;
import com.vmware.vise.core.model.data;

@data
public class RemoveDiskSpec {
   public DecommissionMode decommissionMode;
   public ScsiDisk[] disks;
}
