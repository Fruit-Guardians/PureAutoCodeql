package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vise.core.model.data;
import java.util.List;

@data
public class HostVnicsData {
   public String hostName;
   public List<PerfVnicEntity> vnics;
}
