package com.vmware.vsphere.client.vsan.health;

import com.vmware.vise.core.model.data;

@data
public enum ColumnType {
   mor,
   listMor,
   vsanObjectUuid,
   health,
   string,
   Long,
   Float,
   dynamic,
   HostReference,
   vsanObjectHealth,
   vsanDataProtectionObjectHealth,
   date;
}
