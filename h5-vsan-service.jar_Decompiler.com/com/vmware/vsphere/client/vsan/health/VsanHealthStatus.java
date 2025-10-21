package com.vmware.vsphere.client.vsan.health;

import com.vmware.vise.core.model.data;

@data
public enum VsanHealthStatus {
   red,
   yellow,
   green,
   skipped,
   info,
   unknown,
   warning;
}
