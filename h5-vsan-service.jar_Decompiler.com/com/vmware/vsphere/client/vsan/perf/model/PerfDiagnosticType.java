package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vise.core.model.data;

@data
public enum PerfDiagnosticType {
   eval,
   tput,
   iops,
   lat;
}
