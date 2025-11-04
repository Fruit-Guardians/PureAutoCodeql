package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso;

import com.vmware.vim.sso.client.SamlToken;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class Token {
   protected final PrivateKey key;
   protected final X509Certificate cert;
   protected final SamlToken saml;

   public Token(PrivateKey key, X509Certificate cert, SamlToken saml) {
      this.key = key;
      this.cert = cert;
      this.saml = saml;
   }

   public PrivateKey getKey() {
      return this.key;
   }

   public X509Certificate getCert() {
      return this.cert;
   }

   public SamlToken getSaml() {
      return this.saml;
   }
}
