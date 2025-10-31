package com.vmware.vsphere.client.vsan.iscsi.models.config;

import com.vmware.vise.core.model.data;
import com.vmware.vsphere.client.vsan.base.data.StoragePolicyData;

@data
public class VsanIscsiConfigEditSpec {
   public Boolean enableIscsiTargetService;
   public String network;
   public Integer port;
   public VsanIscsiAuthSpec authSpec;
   public StoragePolicyData policy;
}
