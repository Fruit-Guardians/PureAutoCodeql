package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http;

import com.vmware.vim.vmomi.client.http.ThumbprintVerifier;
import com.vmware.vim.vmomi.client.http.ThumbprintVerifier.Result;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.HashUtil;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import javax.net.ssl.SSLException;

public class ShaThumbprintVerifier implements ThumbprintVerifier {
   protected final String thumbprint;
   protected final String algo;
   protected final HashUtil hashUtil;

   public ShaThumbprintVerifier(String thumbprint, HashUtil hashUtil) {
      this.hashUtil = hashUtil;
      this.thumbprint = hashUtil.parseSha(thumbprint);
      this.algo = hashUtil.extractAlgo(this.thumbprint);
   }

   public Result verify(String thumbprint) {
      return Result.MATCH;
   }

   public void onSuccess(X509Certificate[] chain, String thumbprint, Result verifyResult, boolean trustedChain, boolean verifiedAssertions) throws SSLException {
      if (chain != null && chain.length >= 1) {
         if (!this.hashUtil.verify(this.thumbprint, chain[0])) {
            throw new SSLException("Certificate seen on the network differs from the certificate we expected");
         }
      } else {
         throw new SSLException("Bad certificate chain: " + Arrays.toString(chain));
      }
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
         ShaThumbprintVerifier other = (ShaThumbprintVerifier)obj;
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

   public String toString() {
      return "ShaThumbprintVerifier [thumbprint=" + this.thumbprint + "]";
   }
}
