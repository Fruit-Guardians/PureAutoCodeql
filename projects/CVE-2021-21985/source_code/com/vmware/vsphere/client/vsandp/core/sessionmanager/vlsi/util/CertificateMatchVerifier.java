package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.util;

import com.vmware.vim.vmomi.client.http.ThumbprintVerifier;
import com.vmware.vim.vmomi.client.http.ThumbprintVerifier.Result;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.CertUtil;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import javax.net.ssl.SSLException;

public class CertificateMatchVerifier implements ThumbprintVerifier {
   protected final X509Certificate certificate;

   public CertificateMatchVerifier(String certificate) {
      this.certificate = CertUtil.parseX509(certificate);
   }

   public CertificateMatchVerifier(X509Certificate certificate) {
      this.certificate = certificate;
   }

   public Result verify(String thumbprint) {
      return Result.MATCH;
   }

   public void onSuccess(X509Certificate[] chain, String thumbprint, Result verifyResult, boolean trustedChain, boolean verifiedAssertions) throws SSLException {
      if (chain != null && chain.length >= 1) {
         if (!this.certificate.equals(chain[0])) {
            throw new SSLException("Certificate seen on the network differs from the certificate we expected");
         }
      } else {
         throw new SSLException("Bad certificate chain: " + Arrays.toString(chain));
      }
   }

   public int hashCode() {
      boolean var10000 = true;
      int result = 1;
      int result = 31 * result + (this.certificate == null ? 0 : this.certificate.hashCode());
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
         CertificateMatchVerifier other = (CertificateMatchVerifier)obj;
         if (this.certificate == null) {
            if (other.certificate != null) {
               return false;
            }
         } else if (!this.certificate.equals(other.certificate)) {
            return false;
         }

         return true;
      }
   }
}
