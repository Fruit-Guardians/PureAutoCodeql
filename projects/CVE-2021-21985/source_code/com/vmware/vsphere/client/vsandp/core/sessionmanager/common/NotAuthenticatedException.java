package com.vmware.vsphere.client.vsandp.core.sessionmanager.common;

import com.vmware.vise.core.model.data;

@data
public class NotAuthenticatedException extends RuntimeException {
   public NotAuthenticatedException() {
   }

   public NotAuthenticatedException(String message) {
      super(message);
   }

   public NotAuthenticatedException(Throwable cause) {
      super(cause);
   }

   public NotAuthenticatedException(String message, Throwable cause) {
      super(message, cause);
   }
}
