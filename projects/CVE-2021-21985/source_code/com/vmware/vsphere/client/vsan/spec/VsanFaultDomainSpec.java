package com.vmware.vsphere.client.vsan.spec;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;

@data
public class VsanFaultDomainSpec extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public String faultDomain;
   public ManagedObjectReference hostRef;
}
