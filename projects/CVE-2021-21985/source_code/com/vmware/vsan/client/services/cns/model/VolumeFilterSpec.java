package com.vmware.vsan.client.services.cns.model;

import com.vmware.vise.core.model.data;

@data
public class VolumeFilterSpec {
   public String id;
   public String name;
   public String datastore;
   public String storagePolicy;
   public String containerCluster;
   public String complianceStatus;
   public String accessibilityStatus;
   public CnsLabel[] labels;
   public long offset;
   public long limit;
}
