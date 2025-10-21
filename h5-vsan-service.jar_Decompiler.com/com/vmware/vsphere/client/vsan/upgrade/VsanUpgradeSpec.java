package com.vmware.vsphere.client.vsan.upgrade;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vise.core.model.data;

@data
public class VsanUpgradeSpec extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public boolean performObjectUpgrade;
   public boolean downgradeFormat;
   public boolean allowReducedRedundancy;
}
