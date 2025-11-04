package com.vmware.vsphere.client.vsandp.workflowbacking.recovery.restore.model;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;

@data
public class GetClosestSyncPointsSpec {
   public Long targetTime;
   public ManagedObjectReference[] vmRefs;
   public boolean restoreOnlyFromLocal;
   public boolean restoreOnlyFromQuiesced;
}
