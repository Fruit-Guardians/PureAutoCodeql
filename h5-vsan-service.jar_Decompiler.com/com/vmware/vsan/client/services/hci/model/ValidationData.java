package com.vmware.vsan.client.services.hci.model;

import com.vmware.vise.core.model.data;
import java.util.List;

@data
public class ValidationData {
   public List<VsanHealthCheck> vsanHealthChecks;
   public boolean isVsanEnabled;

   public ValidationData() {
   }

   public ValidationData(List<VsanHealthCheck> vsanHealthChecks, boolean isVsanEnabled) {
      this.vsanHealthChecks = vsanHealthChecks;
      this.isVsanEnabled = isVsanEnabled;
   }
}
