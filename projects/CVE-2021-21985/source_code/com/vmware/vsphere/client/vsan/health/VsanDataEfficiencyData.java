package com.vmware.vsphere.client.vsan.health;

import com.vmware.vise.core.model.data;

@data
public class VsanDataEfficiencyData {
   public long originalUsedSize;
   public long actualUsedSize;
   public boolean dedupEnabled;
}
