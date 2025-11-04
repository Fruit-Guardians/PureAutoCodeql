package com.vmware.vsphere.client.vsan.data;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vise.core.model.data;

@data
public class VsanDiskGroupData extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public VsanDiskData ssd;
   public VsanDiskData[] disks;
   public boolean mounted = true;
   public boolean unlocked;
   public boolean encrypted;
   public boolean isAllFlash;
}
