package com.vmware.vsan.client.services.cns.model;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;
import com.vmware.vsan.client.services.virtualobjects.data.VirtualObjectsFilter;

@data
public class VolumeVirtualObject {
   public ManagedObjectReference cluster;
   public String uuid;
   public VirtualObjectsFilter virtualObjectsFilter;
}
