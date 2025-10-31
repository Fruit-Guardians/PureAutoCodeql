package com.vmware.vsphere.client.vsan.health;

import com.vmware.vise.core.model.data;

@data
public enum VsanHealthServiceGoalState {
   uninstalled,
   installed,
   enabled,
   unknown;
}
