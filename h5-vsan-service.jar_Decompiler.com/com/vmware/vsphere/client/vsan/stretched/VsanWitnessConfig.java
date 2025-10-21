package com.vmware.vsphere.client.vsan.stretched;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vim.binding.vim.vsan.host.DiskMapping;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;

@data
public class VsanWitnessConfig extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public ManagedObjectReference host;
   public DiskMapping diskMapping;
   public String preferredFaultDomain;
}
