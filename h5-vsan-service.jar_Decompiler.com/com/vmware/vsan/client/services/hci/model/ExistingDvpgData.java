package com.vmware.vsan.client.services.hci.model;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;

@data
public class ExistingDvpgData {
   public ManagedObjectReference dvpgRef;
   public String name;
   public boolean isSelected;
}
