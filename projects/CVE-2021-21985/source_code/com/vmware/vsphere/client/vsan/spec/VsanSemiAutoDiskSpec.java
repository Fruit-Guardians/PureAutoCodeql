package com.vmware.vsphere.client.vsan.spec;

import com.vmware.vim.binding.vim.host.ScsiDisk;
import com.vmware.vise.core.model.data;
import com.vmware.vsphere.client.vsan.data.ClaimOption;

@data
public class VsanSemiAutoDiskSpec {
   public ScsiDisk disk;
   public ClaimOption claimOption;
   public boolean markedAsFlash;
}
