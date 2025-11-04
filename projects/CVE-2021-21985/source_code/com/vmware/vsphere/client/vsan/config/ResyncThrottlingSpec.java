package com.vmware.vsphere.client.vsan.config;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vise.core.model.data;

@data
public class ResyncThrottlingSpec extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public int iopsLimit;
}
