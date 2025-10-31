package com.vmware.vsan.client.services.dataprotection.model;

import com.vmware.vise.core.model.data;
import com.vmware.vsphere.client.vsan.base.data.VsanObjectDataProtectionHealthState;
import java.util.Date;

@data
public class DpReplicationItemData extends ProtectionItem {
   public String key;
   public String name;
   public VsanObjectDataProtectionHealthState healthState;
   public RecoveryState recoveryState;
   public Date latestRecoveryPoint;

   public String getName() {
      return this.name;
   }

   public DpReplicationItemData(String keyArg, String nameArg, String healthStateArg, String recoveryStateArg, Date latestRecoveryPointArg) {
      this.key = keyArg;
      this.name = nameArg;
      this.healthState = VsanObjectDataProtectionHealthState.fromProtectionStateName(healthStateArg);
      this.recoveryState = RecoveryState.forName(recoveryStateArg);
      this.latestRecoveryPoint = latestRecoveryPointArg;
   }

   public String toString() {
      StringBuilder sb = new StringBuilder(String.format("%s [key = %s, name = %s, healthState = %s, recoveryState = %s, lastRecoveryPoint = ", this.getClass().getName(), this.key, this.name, this.healthState, this.recoveryState));
      String dateTime = this.latestRecoveryPoint == null ? "null" : String.format("%1$tY-%5$tm-%1$td %1$tH:%1$tM:%1$tS", this.latestRecoveryPoint);
      sb.append(dateTime);
      sb.append("]");
      return sb.toString();
   }
}
