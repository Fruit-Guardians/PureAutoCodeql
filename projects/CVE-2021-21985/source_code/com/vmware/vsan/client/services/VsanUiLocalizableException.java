package com.vmware.vsan.client.services;

public class VsanUiLocalizableException extends RuntimeException {
   private String errorKey;
   private Object[] params;

   public VsanUiLocalizableException(String errorKey) {
      this(errorKey, (String)null, (Throwable)null);
   }

   public VsanUiLocalizableException(String errorKey, Object[] params) {
      this(errorKey, (String)null, (Throwable)null, params);
   }

   public VsanUiLocalizableException(String errorKey, String message) {
      this(errorKey, message, (Throwable)null);
   }

   public VsanUiLocalizableException(String errorKey, Throwable cause) {
      this(errorKey, (String)null, cause);
   }

   public VsanUiLocalizableException(String errorKey, String message, Throwable cause, Object... params) {
      super(message, cause);
      this.errorKey = errorKey;
      this.params = params;
   }

   public String getErrorKey() {
      return this.errorKey;
   }

   public Object[] getParams() {
      return this.params;
   }
}
