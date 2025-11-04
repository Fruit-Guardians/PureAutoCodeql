package com.vmware.vsphere.client.vsan.data;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vim.binding.vim.vsan.host.DiskResult;
import com.vmware.vise.core.model.data;

@data
public class VsanVirtualPhysicalMappingData extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public DiskResult[] disks;
   public VsanHostData[] hosts;
}
