package com.vmware.vsphere.client.vsandp.workflowbacking.recovery.restore.model;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;
import com.vmware.vsphere.client.vsandp.data.DataProtectionInstance;

@data
public class RestoreVmSpec {
   public DataProtectionInstance selectedSyncPoint;
   public String storagePolicyId;
   public ManagedObjectReference selectedVmFolder;
   public ManagedObjectReference selectedNetwork;
   public ManagedObjectReference selectedResourcePool;
   public String vmName;
   public boolean powerOn;
   public boolean createIndependentVm;
}
