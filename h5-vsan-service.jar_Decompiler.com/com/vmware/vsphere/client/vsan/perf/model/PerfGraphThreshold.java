package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vise.core.model.data;

@data
public class PerfGraphThreshold {
   public Long yellow;
   public Long red;
   public PerfGraphThresholdDirection direction;
}
