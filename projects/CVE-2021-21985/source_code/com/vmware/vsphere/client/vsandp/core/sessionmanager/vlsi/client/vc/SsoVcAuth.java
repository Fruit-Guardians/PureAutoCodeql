package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc;

import com.vmware.vim.binding.vim.SessionManager;
import com.vmware.vim.sso.client.SamlToken;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.CheckedRunnable;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.util.RequestContextUtil;
import java.security.PrivateKey;

public class SsoVcAuth extends VcAuthenticator {
   protected final PrivateKey privateKey;
   protected final SamlToken token;

   public SsoVcAuth(PrivateKey privateKey, SamlToken token, String locale) {
      super(locale);
      this.privateKey = privateKey;
      this.token = token;
   }

   protected void loginVc(final VcConnection connection) {
      CheckedRunnable.withoutChecked(new CheckedRunnable() {
         public void run() throws Exception {
            SessionManager sm = connection.getSessionManager();
            RequestContextUtil.withSignInfo(sm, SsoVcAuth.this.privateKey, SsoVcAuth.this.token);
            connection.setSession(sm.loginByToken(SsoVcAuth.this.locale));
         }
      });
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
         SsoVcAuth other = (SsoVcAuth)obj;
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
