package com.vmware.vsan.client.services.fileservice.model;

import com.vmware.vim.vsan.binding.vim.vsan.FileShare;
import com.vmware.vise.core.model.data;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

@data
public class VsanFileServiceShare {
   private static final int RED_THRESHOLD = 80;
   private static final Log logger = LogFactory.getLog(VsanFileServiceShare.class);
   public String uuid;
   public List<String> objectUuids;
   public VsanFileServiceShareConfig config;
   public long usedCapacity;
   public int usageOverQuota;
   public boolean isOverRedThreshold;
   public boolean isOverSoftQuota;

   public static VsanFileServiceShare fromVmodl(FileShare vmodl) {
      VsanFileServiceShare share = new VsanFileServiceShare();
      share.uuid = vmodl.uuid;
      if (vmodl.runtime != null && ArrayUtils.isNotEmpty(vmodl.runtime.vsanObjectUuids)) {
         share.objectUuids = Arrays.asList(vmodl.runtime.vsanObjectUuids);
      } else {
         logger.warn("No vsanObjectUuids are assigned to share: " + share.uuid);
      }

      share.config = VsanFileServiceShareConfig.fromVmodl(vmodl.config);
      if (vmodl.runtime != null && vmodl.runtime.usedCapacity != null) {
         share.usedCapacity = vmodl.runtime.usedCapacity;
         share.updateUsageOverQuota();
      }

      return share;
   }

   public void updateUsageOverQuota() {
      this.usageOverQuota = 0;
      this.isOverSoftQuota = false;
      this.isOverRedThreshold = false;
      if (this.usedCapacity != 0L && this.config != null) {
         double softQuotaInBytes;
         if (this.config.quota != null && this.config.quota != 0.0D && this.config.quotaSize != null) {
            softQuotaInBytes = this.config.quotaSize.multiplier * this.config.quota;
            double ratio = (double)this.usedCapacity / softQuotaInBytes;
            this.usageOverQuota = (int)Math.round(ratio * 100.0D);
            this.isOverRedThreshold = this.usageOverQuota > 80;
         }

         if (this.config.softQuota != null && this.config.softQuotaSize != null) {
            softQuotaInBytes = this.config.softQuotaSize.multiplier * this.config.softQuota;
            this.isOverSoftQuota = (double)this.usedCapacity > softQuotaInBytes;
         }

      }
   }
}
