package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vise.core.model.data;
import java.util.List;

@data
public class HostPnicsData {
   public String hostName;
   public List<PerfPhysicalAdapterEntity> pnics;
}
