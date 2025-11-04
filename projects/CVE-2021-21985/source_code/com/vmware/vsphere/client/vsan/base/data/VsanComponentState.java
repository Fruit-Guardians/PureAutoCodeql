package com.vmware.vsphere.client.vsan.base.data;

import com.vmware.vise.core.model.data;

@data
public enum VsanComponentState {
   ACTIVE,
   ACTIVE_STALE,
   ABSENT,
   ABSENT_RESYNC,
   DEGRADED,
   RECONFIG,
   UNKNOWN;
}
