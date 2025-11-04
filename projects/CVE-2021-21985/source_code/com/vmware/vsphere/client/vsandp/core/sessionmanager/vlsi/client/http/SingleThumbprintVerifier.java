package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http;

import com.vmware.vim.vmomi.client.http.ThumbprintVerifier;
import com.vmware.vim.vmomi.client.http.ThumbprintVerifier.Result;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLException;

public class SingleThumbprintVerifier implements ThumbprintVerifier {
   protected final String thumbprint;

   public SingleThumbprintVerifier(String thumbprint) {
      this.thumbprint = thumbprint.toLowerCase();
   }

   public Result verify(String thumbprint) {
      return thumbprint.toLowerCase().equals(this.thumbprint) ? Result.MATCH : Result.MISMATCH;
   }

   public void onSuccess(X509Certificate[] chain, String thumbprint, Result verifyResult, boolean trustedChain, boolean verifiedAssertions) throws SSLException {
   }

   public int hashCode() {
      boolean var10000 = true;
      int result = 1;
      int result = 31 * result + (this.thumbprint == null ? 0 : this.thumbprint.hashCode());
      return result;
   }

   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj == null) {
         return false;
      } else if (this.getClass() != obj.getClass()) {
         return false;
      } else {
         SingleThumbprintVerifier other = (SingleThumbprintVerifier)obj;
         if (this.thumbprint == null) {
            if (other.thumbprint != null) {
               return false;
            }
         } else if (!this.thumbprint.equals(other.thumbprint)) {
            return false;
         }

         return true;
      }
   }
}
