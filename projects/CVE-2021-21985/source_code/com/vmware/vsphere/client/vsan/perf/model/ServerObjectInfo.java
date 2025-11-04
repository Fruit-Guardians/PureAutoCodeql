package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vise.core.model.data;

@data
public class ServerObjectInfo {
   public String name;
   public String vsanUuid;
   public boolean isCluster;
}
