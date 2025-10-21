package com.vmware.vsphere.client.vsandp.workflowbacking.recovery.restore.model;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;

@data
public class VmInventoryModel {
   public VmInventoryModel.InventoryData folder = new VmInventoryModel.InventoryData();
   public VmInventoryModel.InventoryData compute = new VmInventoryModel.InventoryData();
   public VmInventoryModel.InventoryData network = new VmInventoryModel.InventoryData();
   public boolean folderSameAsSource;
   public boolean computeSameAsSource;
   public boolean networkSameAsSource;
   public ManagedObjectReference rootDc;
   public ManagedObjectReference rootVsanCluster;

   @data
   public static class InventoryData {
      public String name;
      public String iconId;
      public ManagedObjectReference ref;
   }
}
