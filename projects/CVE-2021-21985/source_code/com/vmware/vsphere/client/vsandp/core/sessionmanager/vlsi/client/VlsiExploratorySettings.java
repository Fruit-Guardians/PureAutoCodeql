package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client;

import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.ResourceFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.LookupSvcConnection;
import java.util.UUID;

public class VlsiExploratorySettings {
   private final VlsiSettings serviceSettingsTemplate;
   private final ResourceFactory<LookupSvcConnection, VlsiSettings> lookupSvcFactory;
   private final VlsiSettings lookupSvcSettings;
   private final UUID serviceUuid;

   public VlsiExploratorySettings(VlsiSettings serviceSettingsTemplate, ResourceFactory<LookupSvcConnection, VlsiSettings> lookupSvcFactory, VlsiSettings lookupSvcSettings, UUID serviceUuid) {
      this.serviceSettingsTemplate = serviceSettingsTemplate;
      this.lookupSvcFactory = lookupSvcFactory;
      this.lookupSvcSettings = lookupSvcSettings;
      this.serviceUuid = serviceUuid;
   }

   public VlsiSettings getServiceSettingsTemplate() {
      return this.serviceSettingsTemplate;
   }

   public ResourceFactory<LookupSvcConnection, VlsiSettings> getLookupSvcFactory() {
      return this.lookupSvcFactory;
   }

   public VlsiSettings getLookupSvcSettings() {
      return this.lookupSvcSettings;
   }

   public UUID getServiceUuid() {
      return this.serviceUuid;
   }

   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (!(o instanceof VlsiExploratorySettings)) {
         return false;
      } else {
         VlsiExploratorySettings that = (VlsiExploratorySettings)o;
         if (!this.lookupSvcFactory.equals(that.lookupSvcFactory)) {
            return false;
         } else if (!this.lookupSvcSettings.equals(that.lookupSvcSettings)) {
            return false;
         } else if (!this.serviceSettingsTemplate.equals(that.serviceSettingsTemplate)) {
            return false;
         } else {
            return this.serviceUuid.equals(that.serviceUuid);
         }
      }
   }

   public int hashCode() {
      int result = this.serviceSettingsTemplate.hashCode();
      result = 31 * result + this.lookupSvcFactory.hashCode();
      result = 31 * result + this.lookupSvcSettings.hashCode();
      result = 31 * result + this.serviceUuid.hashCode();
      return result;
   }

   public String toString() {
      StringBuilder sb = new StringBuilder("VlsiExploratorySettings{");
      sb.append("serviceSettingsTemplate=").append(this.serviceSettingsTemplate);
      sb.append(", lookupSvcFactory=").append(this.lookupSvcFactory);
      sb.append(", lookupSvcSettings=").append(this.lookupSvcSettings);
      sb.append(", serviceUuid=").append(this.serviceUuid);
      sb.append('}');
      return sb.toString();
   }
}
