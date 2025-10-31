package com.vmware.vsphere.client.vsan.whatif;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vise.core.model.data;

@data
public class WhatIfResult extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public WhatIfData noDataMigration;
   public WhatIfData ensureAccessibility;
   public WhatIfData fullDataMigration;
   public Boolean isWhatIfSupported;
}
