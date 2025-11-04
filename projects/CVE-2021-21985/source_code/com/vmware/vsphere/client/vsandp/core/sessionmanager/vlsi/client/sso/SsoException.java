package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso;

public class SsoException extends RuntimeException {
   private static final long serialVersionUID = -2170845508916630604L;

   public SsoException() {
   }

   public SsoException(String message, Throwable cause) {
      super(message, cause);
   }

   public SsoException(String message) {
      super(message);
   }

   public SsoException(Throwable cause) {
      super(cause);
   }

   public static RuntimeException toSsoEx(Throwable e) {
      return (RuntimeException)(e instanceof RuntimeException ? (RuntimeException)e : new SsoException(e));
   }
}
