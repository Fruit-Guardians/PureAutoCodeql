package com.vmware.vsphere.client.vsandp.controllers.vm.summary.model;

import com.vmware.vise.core.model.data;
import com.vmware.vsphere.client.vsan.base.data.VsanObjectDataProtectionHealthState;
import java.util.Date;

@data
public class VmDataProtectionBaseData {
   public VsanObjectDataProtectionHealthState healthState;
   public String lastError;
   public Date latestSyncPoint;
   public int rpoInterval;
   public boolean restoreAvailable = false;
   public Integer quiescingFrequency;
   public Integer retentionCount;
   public long storageUsage;

   public VmDataProtectionBaseData setLatestSyncPoint(Date latestSyncPoint) {
      this.latestSyncPoint = latestSyncPoint;
      return this;
   }

   public String toString() {
      return this.getClass().getSimpleName() + ":/nhealthState='" + this.healthState + "'" + ",/nlastError='" + this.lastError + "'" + ",/nlatestSyncPoint=" + this.latestSyncPoint + ",/nrpoInterval=" + this.rpoInterval + ",/nrestoreAvailable=" + this.restoreAvailable + ",/nquiescingFrequency=" + this.quiescingFrequency + ",/nretentionCount=" + this.retentionCount + ",/nstorageUsage=" + this.storageUsage;
   }
}
