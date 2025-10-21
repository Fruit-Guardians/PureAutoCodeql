package com.vmware.vsan.client.services.obfuscation.model;

import com.vmware.vise.core.model.data;

@data
public class ObfuscationData {
   public boolean ceipEnabled;
   public boolean obfuscationSupported;
   public String clusterVsanConfigUuid;
   public String obfuscationMap;
}
