package com.vmware.vsphere.client.vsan.data;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vise.core.model.data;

@data
public class VsanDiskAndGroupData extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public VsanDiskData[] connectedDisks;
   public VsanDiskData[] ineligibleDisks;
   public VsanDiskData[] disksNotInUse;
   public VsanDiskData[] vsanDisks;
   public VsanDiskGroupData[] vsanGroups;
}
