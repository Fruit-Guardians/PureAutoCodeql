package com.vmware.vsan.client.services.virtualobjects.data;

import com.vmware.vise.core.model.data;

@data
public class VsanObjectHealthData {
   public String vsanHealthState;
   public String vsanDataProtectionHealthState;
   public String policyName;

   public VsanObjectHealthData(String vsanHealthState, String vsanDpHealthState, String policyName) {
      this.vsanHealthState = vsanHealthState;
      this.vsanDataProtectionHealthState = vsanDpHealthState;
      this.policyName = policyName;
   }
}
