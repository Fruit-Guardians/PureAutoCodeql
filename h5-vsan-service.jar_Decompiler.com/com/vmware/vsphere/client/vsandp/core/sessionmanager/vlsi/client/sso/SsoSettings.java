package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso;

import java.security.cert.X509Certificate;

public class SsoSettings {
   protected final SsoEndpoints endpoints;
   protected final X509Certificate[] signingCerts;

   public SsoSettings(SsoEndpoints endpoints, X509Certificate[] signingCerts) {
      this.endpoints = endpoints;
      this.signingCerts = signingCerts;
   }

   public SsoEndpoints getEndpoints() {
      return this.endpoints;
   }

   public X509Certificate[] getSigningCerts() {
      return this.signingCerts;
   }
}
