package com.vmware.vsan.client.services.virtualobjects.data;

import com.vmware.vise.core.model.data;

@data
public enum VirtualObjectsFilter {
   VMS,
   ISCSI_TARGETS,
   FCD_OBJECTS,
   FILE_SHARES,
   VOLUMES,
   OTHERS;
}
