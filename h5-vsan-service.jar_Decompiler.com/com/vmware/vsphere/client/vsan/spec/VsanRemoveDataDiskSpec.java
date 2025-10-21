package com.vmware.vsphere.client.vsan.spec;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vim.binding.vim.host.MaintenanceSpec;
import com.vmware.vim.binding.vim.host.ScsiDisk;
import com.vmware.vise.core.model.data;

@data
public class VsanRemoveDataDiskSpec extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public ScsiDisk[] disks;
   public MaintenanceSpec evacuateData;
}
