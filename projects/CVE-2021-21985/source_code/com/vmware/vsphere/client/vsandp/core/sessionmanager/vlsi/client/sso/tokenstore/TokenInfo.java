package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.tokenstore;

import com.vmware.vim.sso.client.SamlToken;
import java.security.PrivateKey;

public class TokenInfo {
   protected final PrivateKey privateKey;
   protected final SamlToken token;

   public TokenInfo(PrivateKey privateKey, SamlToken token) {
      this.privateKey = privateKey;
      this.token = token;
   }

   public PrivateKey getPrivateKey() {
      return this.privateKey;
   }

   public SamlToken getToken() {
      return this.token;
   }

   public String toString() {
      return "TokenInfo [privateKey=" + this.privateKey + ", token=" + this.token + "]";
   }
}
