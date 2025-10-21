package com.vmware.vsan.client.services.inventory;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;

@data
public class InventoryEntryData {
   public ManagedObjectReference nodeRef;
   public String name;
   public boolean isLeafNode;
   public String iconShape;
   public boolean connected;
   public boolean isDrsEnabled;
}
