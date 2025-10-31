package com.vmware.vsan.client.services.cns.model;

import com.vmware.vise.core.model.data;

@data
public class VolumeFilterResult {
   public Volume[] volumes;
   public long total;
}
