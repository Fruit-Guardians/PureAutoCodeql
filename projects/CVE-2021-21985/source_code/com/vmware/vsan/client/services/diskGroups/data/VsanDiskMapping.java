package com.vmware.vsan.client.services.diskGroups.data;

import com.vmware.vim.binding.vim.host.ScsiDisk;
import com.vmware.vim.binding.vim.vsan.host.DiskMapping;
import com.vmware.vise.core.model.data;
import org.apache.commons.lang.ArrayUtils;

@data
public class VsanDiskMapping {
   public ScsiDisk ssd;
   public ScsiDisk[] nonSsd;

   public DiskMapping toVmodl() {
      if (this.ssd != null && !ArrayUtils.isEmpty(this.nonSsd)) {
         DiskMapping result = new DiskMapping();
         result.ssd = this.ssd;
         result.nonSsd = this.nonSsd;
         return result;
      } else {
         return null;
      }
   }
}
