package com.vmware.vsan.client.services.hci.model;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;

@data
public class ExistingDvsData {
   public ManagedObjectReference dvsRef;
   public String name;
   public String version;
   public String niocVersion;
   public String lacpVersion;
   public boolean isSelected;
}
