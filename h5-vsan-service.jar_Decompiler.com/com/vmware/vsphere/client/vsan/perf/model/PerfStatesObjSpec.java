package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;

@data
public class PerfStatesObjSpec {
   public ManagedObjectReference clusterRef;
   public String profileId;
   public boolean isVerboseEnabled;
   public boolean isNetworkDiagnosticModeEnabled;
}
