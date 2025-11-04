package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.dp;

import com.vmware.vim.vmomi.core.Stub;
import com.vmware.vim.vmomi.core.impl.RequestContextImpl;
import com.vmware.vim.vmomi.core.security.SignInfo;
import com.vmware.vim.vmomi.core.security.impl.SignInfoImpl;
import com.vmware.vim.vsandp.binding.vim.vsandp.dps.SessionManager;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.Authenticator;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.tokenstore.TokenInfo;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.tokenstore.TokenStore;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public final class DpTokenAuthenticator extends Authenticator {
   private static final Log logger = LogFactory.getLog(DpTokenAuthenticator.class);
   protected final String locale;
   protected final TokenStore tokenStore;
   protected final String siteId;

   public DpTokenAuthenticator(String locale, TokenStore tokenStore, String siteId) {
      this.locale = locale;
      this.tokenStore = tokenStore;
      this.siteId = siteId;
   }

   public void login(VlsiConnection connection) {
      this.doLogin((DpConnection)connection);
   }

   private void doLogin(DpConnection connection) {
      TokenInfo tokenInfo = this.tokenStore.retrieveTokenInfo(this.siteId);
      SignInfo signInfo = new SignInfoImpl(tokenInfo.getPrivateKey(), tokenInfo.getToken());
      RequestContextImpl sessionContext = new RequestContextImpl();
      sessionContext.setSignInfo(signInfo);

      try {
         SessionManager sessionManager = connection.getSessionManager();
         ((Stub)sessionManager)._setRequestContext(sessionContext);
         sessionManager.loginByToken(this.locale);
         logger.info("Authenticated " + connection + ", token expiring: " + tokenInfo.getToken().getExpirationTime());
      } catch (Exception var6) {
         logger.error("Failed to login with token: " + tokenInfo.getToken(), var6);
         throw new RuntimeException("Failed to login", var6);
      }
   }

   public void logout(VlsiConnection connection) {
      this.doLogout((DpConnection)connection);
   }

   private void doLogout(DpConnection connection) {
      connection.getSessionManager().logout();
   }

   public int hashCode() {
      boolean var10000 = true;
      int result = super.hashCode();
      result = 31 * result + (this.siteId == null ? 0 : this.siteId.hashCode());
      result = 31 * result + (this.tokenStore == null ? 0 : this.tokenStore.hashCode());
      return result;
   }

   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (!super.equals(obj)) {
         return false;
      } else if (this.getClass() != obj.getClass()) {
         return false;
      } else {
         DpTokenAuthenticator other = (DpTokenAuthenticator)obj;
         if (this.siteId == null) {
            if (other.siteId != null) {
               return false;
            }
         } else if (!this.siteId.equals(other.siteId)) {
            return false;
         }

         if (this.tokenStore == null) {
            if (other.tokenStore != null) {
               return false;
            }
         } else if (!this.tokenStore.equals(other.tokenStore)) {
            return false;
         }

         return true;
      }
   }
}
