package com.vmware.vsphere.client.vsan.config;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vim.vsan.binding.vim.vsan.DataEfficiencyConfig;
import com.vmware.vise.core.model.data;

@data
public class DataEfficiencySpec extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public boolean deduplicationState;
   public boolean compressionState;

   public DataEfficiencyConfig toVmodlSpec() {
      DataEfficiencyConfig vmodlSpec = new DataEfficiencyConfig();
      vmodlSpec.setDedupEnabled(this.deduplicationState);
      vmodlSpec.setCompressionEnabled(this.compressionState);
      return vmodlSpec;
   }
}
