package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vise.core.model.data;

@data
public enum PerformanceObjectType {
   clusterVmConsumption,
   clusterBackend,
   clusterDomOwner,
   hostBackend,
   hostVmConsumption,
   hostPnic,
   hostVnic,
   hostNet,
   diskGroup,
   cacheDisk,
   capacityDisk,
   vm,
   virtualDisk,
   vscsi,
   cmmds,
   clomDiskStats,
   clomHostStats;
}
