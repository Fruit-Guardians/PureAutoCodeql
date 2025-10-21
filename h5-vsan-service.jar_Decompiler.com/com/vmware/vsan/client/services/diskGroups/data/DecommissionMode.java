package com.vmware.vsan.client.services.diskGroups.data;

import com.vmware.vise.core.model.data;

@data
public enum DecommissionMode {
   noAction,
   ensureObjectAccessibility,
   evacuateAllData;
}
