package com.vmware.vsphere.client.vsan.iscsi.models;

import com.vmware.vise.core.model.data;

@data
public class VsanIscsiTargetProviderParameter {
   public boolean requestNamespaceCapabilityMetadata;
   public boolean requestStorageProfiles;

   public VsanIscsiTargetProviderParameter() {
   }

   public VsanIscsiTargetProviderParameter(boolean requestNamespaceCapabilityMetadata, boolean requestStoragePolicie) {
      this.requestNamespaceCapabilityMetadata = requestNamespaceCapabilityMetadata;
      this.requestStorageProfiles = requestStoragePolicie;
   }
}
