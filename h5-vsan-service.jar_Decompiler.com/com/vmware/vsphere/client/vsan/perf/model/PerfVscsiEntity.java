package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vise.core.model.data;

@data
public class PerfVscsiEntity {
   public Integer busId;
   public Integer position;
   public String vmdkName;
   public String deviceName;
   public int controllerKey;
}
