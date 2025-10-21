package com.vmware.vsphere.client.vsan.stretched;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vise.core.model.data;

@data
public class PreferredFaultDomainData extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public String preferredFaultDomainName;
}
