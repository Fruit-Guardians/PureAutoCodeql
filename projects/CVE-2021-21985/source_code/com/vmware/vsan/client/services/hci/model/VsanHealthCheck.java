package com.vmware.vsan.client.services.hci.model;

import com.vmware.vise.core.model.data;
import com.vmware.vsphere.client.vsan.health.VsanHealthStatus;

@data
public class VsanHealthCheck {
   public String perspective;
   public String healthGroup;
   public String healthTest;
   public String healthCheckLabel;
   public VsanHealthStatus status;

   public VsanHealthCheck() {
   }

   public VsanHealthCheck(String perspective, String healthGroup, String healthTest, String healthCheckLabel, String healthStatus) {
      this.perspective = perspective;
      this.healthGroup = healthGroup;
      this.healthTest = healthTest;
      this.healthCheckLabel = healthCheckLabel;
      this.status = VsanHealthStatus.valueOf(healthStatus);
   }
}
