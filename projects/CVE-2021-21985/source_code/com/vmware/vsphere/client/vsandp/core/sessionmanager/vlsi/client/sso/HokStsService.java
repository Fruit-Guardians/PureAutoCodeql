package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso;

import com.vmware.vim.sso.client.SamlToken;
import com.vmware.vim.sso.client.TokenSpec;
import com.vmware.vim.sso.client.SecurityTokenServiceConfig.HolderOfKeyConfig;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.X509Certificate;

public class HokStsService extends StsService {
   protected final PrivateKey privateKey;
   protected final X509Certificate cert;

   public HokStsService(ServiceEndpoint endpoint, X509Certificate[] signingCerts, PrivateKey privateKey, X509Certificate cert) {
      super(endpoint, signingCerts, new HolderOfKeyConfig(privateKey, cert, (Provider)null));
      this.privateKey = privateKey;
      this.cert = cert;
   }

   public PrivateKey getPrivateKey() {
      return this.privateKey;
   }

   public X509Certificate getCert() {
      return this.cert;
   }

   public SamlToken acquireSolutionToken() {
      return this.acquireSolutionToken(this.getDefaultTokenSpec());
   }

   public SamlToken acquireSolutionToken(TokenSpec tokenSpec) {
      try {
         return this.stsClient.acquireTokenByCertificate(tokenSpec);
      } catch (Exception var3) {
         throw SsoException.toSsoEx(var3);
      }
   }
}
