package com.vmware.vsphere.client.vsan.config;

import com.vmware.vim.binding.vim.vsan.cluster.ConfigInfo;
import com.vmware.vim.binding.vim.vsan.cluster.ConfigInfo.HostDefaultInfo;
import com.vmware.vise.core.model.data;

@data
public class VsanSpec {
   public boolean isEnabled;
   public boolean isAutoClaimMode;
   public DataEfficiencySpec dataEfficiency;
   public boolean allowReducedRedundancy;
   public boolean isEncryptionEnabled;
   public String kmipClusterId;
   public boolean eraseDisksBeforeUse;

   public ConfigInfo toVmodlSpec() {
      ConfigInfo vmodlSpec = new ConfigInfo();
      vmodlSpec.setEnabled(this.isEnabled);
      HostDefaultInfo defaultInfo = new HostDefaultInfo();
      defaultInfo.setAutoClaimStorage(this.isAutoClaimMode);
      vmodlSpec.setDefaultConfig(defaultInfo);
      return vmodlSpec;
   }
}
