package com.vmware.vsphere.client.vsandp.core.sessionmanager.common;

import com.vmware.vim.sso.client.TokenSpec;
import com.vmware.vim.sso.client.TokenSpec.Builder;
import com.vmware.vim.sso.client.TokenSpec.DelegationSpec;
import com.vmware.vise.security.ClientSessionEndListener;
import com.vmware.vise.usersession.UserSessionService;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiSettings;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.SsoAdminFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.tokenstore.ExplorationCapableTokenStore;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.tokenstore.NgcTokenRetriever;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.tokenstore.RenewingTokenRetriever;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.tokenstore.TokenRetriever;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.tokenstore.TokenStore;
import java.security.cert.X509Certificate;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SsoClient extends SessionLocal<TokenStore> implements ClientSessionEndListener {
   private static final long DEFAULT_INITIAL_TOKEN_LIFETIME = 3600L;
   protected final Logger logger = LoggerFactory.getLogger(this.getClass());
   @Autowired
   protected LookupSvcClient lsClient;
   @Autowired
   LookupSvcLocator lsLocator;
   @Autowired
   protected ScheduledExecutorService scheduler;
   @Autowired
   protected SsoAdminFactory ssoFactory;
   @Autowired
   protected UserSessionService sessionService;

   public TokenStore getTokenStore() {
      return (TokenStore)this.get();
   }

   public void authenticateSite(String vcUuid, TokenRetriever tokenRetriever) {
      TokenStore tokenStore = this.getTokenStore();
      tokenStore.addSite(vcUuid.toLowerCase(), tokenRetriever);
   }

   public TokenRetriever newRemoteTokenRetriever(LookupSvcInfo lsInfo, String username, String password, TokenSpec spec) throws Exception {
      return new RenewingTokenRetriever(this.lsLocator.getPrivateKey(), (X509Certificate)this.lsLocator.getH5Keystore().getCertificate("vsphere-webclient"), this.lsClient.getSettings(lsInfo), this.lsClient.getProducerFactory(), this.ssoFactory, this.scheduler, username, password, spec);
   }

   public TokenRetriever newRemoteTokenRetriever(LookupSvcInfo lsInfo, String username, String password) throws Exception {
      TokenSpec tokenSpec = (new Builder(3600L)).renewable(true).delegationSpec(new DelegationSpec(true, (String)null)).createTokenSpec();
      return this.newRemoteTokenRetriever(lsInfo, username, password, tokenSpec);
   }

   public TokenRetriever newLocalTokenRetriever() throws Exception {
      VlsiSettings vlsiSettings = this.lsClient.getSettings(this.lsClient.getLocalLsInfo());
      TokenRetriever localSsoTokenRetriever = new NgcTokenRetriever(this.lsLocator.getPrivateKey(), (X509Certificate)this.lsLocator.getH5Keystore().getCertificate("vsphere-webclient"), vlsiSettings, this.lsClient.getProducerFactory(), this.ssoFactory, this.sessionService);
      return localSsoTokenRetriever;
   }

   public void sessionEnded(String clientId) {
      if (this.logger.isTraceEnabled()) {
         this.logger.trace("Session ended: {}", this.sessionKey());
      }

      this.remove(clientId);
   }

   protected String sessionKey() {
      return this.sessionService.getUserSession().clientId;
   }

   protected TokenStore create() {
      try {
         return new ExplorationCapableTokenStore(this.newLocalTokenRetriever(), this.lsClient);
      } catch (Exception var2) {
         throw new RuntimeException(var2);
      }
   }

   protected void destroy(TokenStore entity) {
      entity.shutdown();
   }

   @PreDestroy
   protected void clear() {
      super.clear();
   }

   public String toString() {
      return this.getClass().getSimpleName();
   }
}
