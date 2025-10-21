package com.vmware.vsphere.client.vsan.base.data;

import com.vmware.vise.core.model.data;

@data
public enum VsanOperationalStatus {
   HEALTHY,
   HEALTHY_TRANSITIONAL,
   UNHEALTHY_TRANSITIONAL,
   UNHEALTHY_DISK_UNAVAILABLE;
}
