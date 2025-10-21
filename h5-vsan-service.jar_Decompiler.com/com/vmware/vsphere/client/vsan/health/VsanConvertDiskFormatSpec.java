package com.vmware.vsphere.client.vsan.health;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;

@data
public class VsanConvertDiskFormatSpec extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public ManagedObjectReference clusterRef;
   public Boolean allowReducedRedundancy;
}
