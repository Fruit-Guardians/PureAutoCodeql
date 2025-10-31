package com.vmware.vsan.client.services.cns.model;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;

@data
public class VolumeDatastoreData {
   public String name;
   public ManagedObjectReference reference;
}
