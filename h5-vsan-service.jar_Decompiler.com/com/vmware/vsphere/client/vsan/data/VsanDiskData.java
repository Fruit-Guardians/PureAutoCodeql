package com.vmware.vsphere.client.vsan.data;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vim.binding.vim.host.ScsiDisk;
import com.vmware.vise.core.model.data;

@data
public class VsanDiskData extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public ScsiDisk disk;
   public boolean inUse;
   public boolean ineligible;
   public String stateReason;
   public String[] issues;
   public String vsanUuid;
   public String diskGroupUuid;
   public boolean isCacheDisk;
   public boolean markedAsCapacityFlash;
   public DiskLocalityType diskLocality;
   public ClaimOption recommendedAllFlashClaimOption;
   public ClaimOption recommendedHybridClaimOption;
   public ClaimOption[] possibleClaimOptions;
   public ClaimOption[] possibleClaimOptionsIfMarkedAsOppositeType;

   public VsanDiskData() {
      this.recommendedAllFlashClaimOption = ClaimOption.DoNotClaim;
      this.recommendedHybridClaimOption = ClaimOption.DoNotClaim;
   }
}
