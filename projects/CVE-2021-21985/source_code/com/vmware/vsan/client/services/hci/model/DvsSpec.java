package com.vmware.vsan.client.services.hci.model;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;

@data
public class DvsSpec {
   public String name;
   public Service[] services;
   public HostAdapter[] adapters;
   public ManagedObjectReference existingDvsMor;
}
