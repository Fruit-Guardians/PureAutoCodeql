package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.tokenstore;

public class IllegalAuthInfoException extends TokenStoreException {
   public IllegalAuthInfoException(String message) {
      super(message);
   }

   public IllegalAuthInfoException(String message, Throwable cause) {
      super(message, cause);
   }

   public IllegalAuthInfoException(Throwable cause) {
      super(cause);
   }

   public IllegalAuthInfoException() {
   }
}
