package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.explorer;

import com.vmware.vim.binding.lookup.ServiceRegistration.Attribute;
import com.vmware.vim.binding.lookup.ServiceRegistration.Endpoint;
import com.vmware.vim.binding.lookup.ServiceRegistration.Info;
import java.net.URI;
import java.util.UUID;

public abstract class AbstractLsRegistration {
   protected final Info info;

   public AbstractLsRegistration(Info info) {
      this.info = info;
   }

   public UUID getUuid() {
      return UUID.fromString(this.info.getServiceId());
   }

   public String getOwnerId() {
      return this.info.getOwnerId();
   }

   public String getVersion() {
      return this.info.getServiceVersion();
   }

   public URI getServiceUrl() {
      return this.getDefaultEndpoint().getUrl();
   }

   public String[] getSslTrust() {
      return this.getDefaultEndpoint().getSslTrust();
   }

   public String toString() {
      return String.format("%s [uuid=%s]", this.getClass().getSimpleName(), this.info.getServiceId());
   }

   protected Attribute findAttribute(String attrName) {
      Attribute[] var5;
      int var4 = (var5 = this.info.getServiceAttributes()).length;

      for(int var3 = 0; var3 < var4; ++var3) {
         Attribute a = var5[var3];
         if (a.key.equals(attrName)) {
            return a;
         }
      }

      throw new IllegalStateException("Attribute not found: " + attrName);
   }

   protected Endpoint findEndpoint(String type) {
      Endpoint[] var5;
      int var4 = (var5 = this.info.getServiceEndpoints()).length;

      for(int var3 = 0; var3 < var4; ++var3) {
         Endpoint e = var5[var3];
         if (e.getEndpointType().getType().equals(type)) {
            return e;
         }
      }

      throw new IllegalStateException("Endpoint not found: " + type);
   }

   protected Endpoint getDefaultEndpoint() {
      if (this.info.serviceEndpoints.length == 1) {
         return this.info.getServiceEndpoints()[0];
      } else {
         throw new IllegalStateException("Could not determine default endpoint, only one expected in query result.");
      }
   }
}
