package com.vmware.vsphere.client.vsan.health;

import com.vmware.vise.core.model.data;

@data
public class VsanWhatIfCapacityModel {
   public boolean isWhatIfCapacitySupported;
   public long freeWhatifCapacityB;
}
