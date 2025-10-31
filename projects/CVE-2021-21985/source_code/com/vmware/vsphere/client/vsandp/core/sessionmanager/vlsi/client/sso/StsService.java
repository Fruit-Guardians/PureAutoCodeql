package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso;

import com.vmware.vim.sso.client.DefaultSecurityTokenServiceFactory;
import com.vmware.vim.sso.client.SamlToken;
import com.vmware.vim.sso.client.SecurityTokenService;
import com.vmware.vim.sso.client.SecurityTokenServiceConfig;
import com.vmware.vim.sso.client.TokenSpec;
import com.vmware.vim.sso.client.SecurityTokenServiceConfig.ConnectionConfig;
import com.vmware.vim.sso.client.SecurityTokenServiceConfig.HolderOfKeyConfig;
import com.vmware.vim.sso.client.TokenSpec.Builder;
import java.net.MalformedURLException;
import java.security.cert.CertStore;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;

public class StsService {
   protected final ServiceEndpoint endpoint;
   protected final SecurityTokenService stsClient;
   protected final int DEFAULT_TOKEN_LIFETIME;

   public StsService(ServiceEndpoint endpoint, X509Certificate[] signingCerts) {
      this(endpoint, signingCerts, (HolderOfKeyConfig)null);
   }

   protected StsService(ServiceEndpoint endpoint, X509Certificate[] signingCerts, HolderOfKeyConfig keyCfg) {
      this.DEFAULT_TOKEN_LIFETIME = 600;
      this.endpoint = endpoint;

      ConnectionConfig connConfig;
      try {
         connConfig = new ConnectionConfig(endpoint.getUrl(), endpoint.getCerts(), (String)null, (CertStore)null, false);
      } catch (MalformedURLException var6) {
         throw SsoException.toSsoEx(var6);
      }

      SecurityTokenServiceConfig config = new SecurityTokenServiceConfig(connConfig, signingCerts, (ExecutorService)null, keyCfg);
      this.stsClient = DefaultSecurityTokenServiceFactory.getSecurityTokenService(config);
   }

   public ServiceEndpoint getEndpoint() {
      return this.endpoint;
   }

   public SecurityTokenService getStsClient() {
      return this.stsClient;
   }

   public TokenSpec getDefaultTokenSpec() {
      return (new Builder(600L)).createTokenSpec();
   }

   public SamlToken acquireBearer(String user, String password) {
      return this.acquireBearerToken(user, password, this.getDefaultTokenSpec());
   }

   public SamlToken acquireBearerToken(String user, String password, TokenSpec tokenSpec) {
      try {
         return this.stsClient.acquireToken(user, password, tokenSpec);
      } catch (Exception var5) {
         throw SsoException.toSsoEx(var5);
      }
   }
}
