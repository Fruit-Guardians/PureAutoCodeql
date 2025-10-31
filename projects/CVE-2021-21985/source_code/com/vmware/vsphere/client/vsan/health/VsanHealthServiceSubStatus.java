package com.vmware.vsphere.client.vsan.health;

import com.vmware.vise.core.model.data;

@data
public enum VsanHealthServiceSubStatus {
   red,
   yellow,
   green,
   unknown;
}
