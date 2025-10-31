package com.vmware.vsphere.client.vsan.util;

import com.vmware.vim.binding.vim.host.DiskDimensions.Lba;
import com.vmware.vsphere.client.vsan.data.ClaimOption;
import com.vmware.vsphere.client.vsan.data.VsanDiskData;
import com.vmware.vsphere.client.vsan.data.VsanSemiAutoClaimDisksData;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;

public abstract class VsanBaseClaimOptionRecommender {
   private VsanSemiAutoClaimDisksData _data;

   public VsanBaseClaimOptionRecommender(VsanSemiAutoClaimDisksData data) {
      this._data = data;
   }

   protected VsanSemiAutoClaimDisksData getData() {
      return this._data;
   }

   public abstract void recommend();

   protected void makeConfigRecommendation(List<VsanDiskData> cacheDisks, List<VsanDiskData> storageDisks, int numExistingGroups) {
      if (CollectionUtils.isEmpty(cacheDisks)) {
         if (numExistingGroups > 0) {
            this.markDisksForClaimingOption(storageDisks, ClaimOption.ClaimForStorage);
         }
      } else if (!CollectionUtils.isEmpty(storageDisks)) {
         this.markDisksForClaimingOption(storageDisks, ClaimOption.ClaimForStorage);
         this.markDisksForClaimingOption(cacheDisks, ClaimOption.ClaimForCache);
      }

   }

   protected abstract void markDisksForClaimingOption(List<VsanDiskData> var1, ClaimOption var2);

   protected static long calculateSize(Lba size) {
      return size.block * (long)size.blockSize;
   }
}
