package com.vmware.vsphere.client.vsan.data;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vim.binding.vim.host.ScsiDisk;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;

@data
public class VsanDiskVmMappingData extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public ScsiDisk disk;
   public ManagedObjectReference[] vm;
}
