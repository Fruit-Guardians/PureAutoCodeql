package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.tokenstore;

public class NoTokenException extends TokenStoreException {
   private final Object siteId;

   public NoTokenException(Object siteId) {
      this(siteId, (Throwable)null);
   }

   public NoTokenException(Object siteId, Throwable cause) {
      super("No token for site: " + siteId, cause);
      this.siteId = siteId;
   }

   public Object getSiteId() {
      return this.siteId;
   }
}
