package com.vmware.vsphere.client.vsan.health;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vise.core.model.data;

@data
public class HardwareOverallHealth extends DataObjectImpl {
   public Integer total;
   public Integer issueCount;
   public String overallStatus;
}
