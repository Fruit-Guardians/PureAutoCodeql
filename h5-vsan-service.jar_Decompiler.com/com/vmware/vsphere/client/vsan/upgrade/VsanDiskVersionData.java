package com.vmware.vsphere.client.vsan.upgrade;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vim.binding.vim.vsan.host.VsanDiskInfo;
import com.vmware.vise.core.model.data;

@data
public class VsanDiskVersionData extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public double version = 1.0D;
   public String vsanUuid;

   public VsanDiskVersionData() {
   }

   public VsanDiskVersionData(VsanDiskInfo vsanDiskInfo) {
      if (vsanDiskInfo != null) {
         this.version = (double)vsanDiskInfo.formatVersion;
         if (this.version == 3.0D) {
            this.version = 2.5D;
         } else if (this.version == 4.0D) {
            this.version = 3.0D;
         } else if (this.version == 0.0D) {
            this.version = 1.0D;
         }

         this.vsanUuid = vsanDiskInfo.vsanUuid;
      }

   }
}
