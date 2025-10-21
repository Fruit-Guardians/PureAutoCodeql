package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vise.core.model.data;
import java.util.List;

@data
public class PerfVirtualMachineDiskData {
   public List<PerfVirtualDiskEntity> virtualDisks;
   public List<PerfVscsiEntity> vscsiEntities;
   public String vmUuid;
}
