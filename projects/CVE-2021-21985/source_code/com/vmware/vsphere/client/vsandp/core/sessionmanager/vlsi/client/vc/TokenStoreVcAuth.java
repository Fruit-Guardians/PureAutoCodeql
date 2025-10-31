package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc;

import com.vmware.vim.binding.vim.SessionManager;
import com.vmware.vim.sso.client.SamlToken;
import com.vmware.vim.vmomi.core.Stub;
import com.vmware.vim.vmomi.core.impl.RequestContextImpl;
import com.vmware.vim.vmomi.core.security.impl.SignInfoImpl;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.CheckedRunnable;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.tokenstore.TokenInfo;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.tokenstore.TokenStore;
import java.security.PrivateKey;

public class TokenStoreVcAuth extends VcAuthenticator {
   protected final TokenStore tokenStore;
   protected final String siteId;

   public TokenStoreVcAuth(String locale, TokenStore tokenStore, String siteId) {
      super(locale);
      this.tokenStore = tokenStore;
      this.siteId = siteId;
   }

   protected void loginVc(final VcConnection connection) {
      CheckedRunnable.withoutChecked(new CheckedRunnable() {
         public void run() throws Exception {
            TokenInfo tokenInfo = TokenStoreVcAuth.this.tokenStore.retrieveTokenInfo(TokenStoreVcAuth.this.siteId);
            PrivateKey privateKey = tokenInfo.getPrivateKey();
            SamlToken token = tokenInfo.getToken();
            RequestContextImpl vlsiReqCtx = new RequestContextImpl();
            vlsiReqCtx.setSignInfo(new SignInfoImpl(privateKey, token));
            SessionManager sm = connection.getSessionManager();
            ((Stub)sm)._setRequestContext(vlsiReqCtx);
            connection.setSession(sm.loginByToken(TokenStoreVcAuth.this.locale));
         }
      });
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
         TokenStoreVcAuth other = (TokenStoreVcAuth)obj;
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
