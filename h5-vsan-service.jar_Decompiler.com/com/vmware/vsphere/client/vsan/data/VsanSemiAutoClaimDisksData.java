package com.vmware.vsphere.client.vsan.data;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vise.core.model.data;

@data
public class VsanSemiAutoClaimDisksData extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public VsanDiskData[] notInUseDisks;
   public int numNotInUseSsdDisks = 0;
   public int numNotInUseDataDisks = 0;
   public int numAllFlashGroups = 0;
   public int numHybridGroups = 0;
   public int numAllFlashCapacityDisks = 0;
   public int numHybridCapacityDisks = 0;
   public boolean hybridDiskGroupExist = false;
   public boolean allFlashDiskGroupExist = false;
   public boolean isAllFlashAvailable = true;
   public long claimedCapacity = 0L;
   public long claimedCache = 0L;
}
