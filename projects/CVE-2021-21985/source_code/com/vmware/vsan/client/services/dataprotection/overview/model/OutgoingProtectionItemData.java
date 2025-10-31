package com.vmware.vsan.client.services.dataprotection.overview.model;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;
import com.vmware.vsan.client.services.dataprotection.model.ProtectionData;
import com.vmware.vsan.client.services.dataprotection.model.ProtectionItem;

@data
public class OutgoingProtectionItemData extends ProtectionItem {
   public String name;
   public ManagedObjectReference moRef;
   public String policyName;
   public String policyId;
   public String namespaceIdentityKey;
   public ProtectionData localProtection;
   public ProtectionData archiveProtection;
   public ProtectionData remoteProtection;

   public String getName() {
      return this.name;
   }

   public String toString() {
      return String.format("%s [name = %s, moRef = %s, policyName = %s, policyId = %s, namespaceIdentityKey = %s localProtection = [%s], archiveProtection = [%s], remoteProtection = [%s]]", this.getClass().getName(), this.name, this.moRef, this.policyName, this.policyId, this.namespaceIdentityKey, this.localProtection, this.archiveProtection, this.remoteProtection);
   }
}
