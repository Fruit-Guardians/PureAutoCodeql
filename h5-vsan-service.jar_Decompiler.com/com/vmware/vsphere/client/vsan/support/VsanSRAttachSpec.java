package com.vmware.vsphere.client.vsan.support;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vise.core.model.data;

@data
public class VsanSRAttachSpec extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public String serviceRequestID;
   public String description;
}
