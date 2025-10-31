package com.vmware.vsphere.client.vsandp.core.sessionmanager.common;

import com.vmware.vise.core.model.data;

@data
public class NotAccessibleException extends RuntimeException {
   public NotAccessibleException() {
   }

   public NotAccessibleException(String message) {
      super(message);
   }

   public NotAccessibleException(Throwable cause) {
      super(cause);
   }

   public NotAccessibleException(String message, Throwable cause) {
      super(message, cause);
   }
}
