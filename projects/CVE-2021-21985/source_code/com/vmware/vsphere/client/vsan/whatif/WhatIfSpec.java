package com.vmware.vsphere.client.vsan.whatif;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;

@data
public class WhatIfSpec extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public String entityUuid;
   public ManagedObjectReference clusterRef;
   public boolean detailed;
}
