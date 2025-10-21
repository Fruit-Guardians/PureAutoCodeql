package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso;

import com.vmware.vim.binding.sso.SessionManager;
import com.vmware.vim.sso.client.SamlToken;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.Authenticator;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.util.RequestContextUtil;
import java.security.PrivateKey;

public class SsoAdminServiceAuthenticator extends Authenticator {
   protected final PrivateKey privateKey;
   protected final SamlToken token;

   public SsoAdminServiceAuthenticator(PrivateKey privateKey, SamlToken token) {
      this.privateKey = privateKey;
      this.token = token;
   }

   public void login(VlsiConnection connection) {
      SessionManager sessionMgr = this.getSessionManager(connection);
      RequestContextUtil.withSignInfo(sessionMgr, this.privateKey, this.token);
      sessionMgr.login();
   }

   public void logout(VlsiConnection connection) {
      this.getSessionManager(connection).logout();
   }

   protected SessionManager getSessionManager(VlsiConnection connection) {
      return (SessionManager)connection.createStub(SessionManager.class, ((SsoAdminConnection)connection).getContent().getSessionManager());
   }

   public PrivateKey getPrivateKey() {
      return this.privateKey;
   }

   public SamlToken getToken() {
      return this.token;
   }

   public int hashCode() {
      boolean var10000 = true;
      int result = super.hashCode();
      result = 31 * result + (this.privateKey == null ? 0 : this.privateKey.hashCode());
      result = 31 * result + (this.token == null ? 0 : this.token.hashCode());
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
         SsoAdminServiceAuthenticator other = (SsoAdminServiceAuthenticator)obj;
         if (this.privateKey == null) {
            if (other.privateKey != null) {
               return false;
            }
         } else if (!this.privateKey.equals(other.privateKey)) {
            return false;
         }

         if (this.token == null) {
            if (other.token != null) {
               return false;
            }
         } else if (!this.token.equals(other.token)) {
            return false;
         }

         return true;
      }
   }
}
