package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vise.core.model.data;
import java.util.List;

@data
public class PerfGraphMetricsData {
   public List<Double> values;
   public PerfGraphThreshold threshold;
   public String key;
   public String subKey;
}
