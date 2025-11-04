package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http;

import com.vmware.vim.vmomi.client.http.ThumbprintVerifier;
import com.vmware.vim.vmomi.client.http.ThumbprintVerifier.Result;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SniffingThumbprintVerifier implements ThumbprintVerifier {
   private static final Logger logger = LoggerFactory.getLogger(SniffingThumbprintVerifier.class);
   private volatile Certificate sniffedCertificate;
   private final boolean passVerification;
   private volatile String thumbprint;

   public SniffingThumbprintVerifier() {
      this(false);
   }

   public SniffingThumbprintVerifier(boolean passVerification) {
      this.passVerification = passVerification;
   }

   public Result verify(String thumbprint) {
      this.thumbprint = thumbprint;
      return Result.MATCH;
   }

   public void onSuccess(X509Certificate[] chain, String thumbprint, Result verifyResult, boolean trustedChain, boolean verifiedAssertions) throws SSLException {
      if (chain != null && chain.length >= 1) {
         this.sniffedCertificate = chain[0];
         if (!this.passVerification) {
            throw new SSLException("Certificate thumbprint verification mismatch");
         }
      } else {
         throw new SSLException("Bad certificate chain: " + Arrays.toString(chain));
      }
   }

   public String getSniffedThumbprint() {
      return this.thumbprint;
   }

   public Certificate getSniffedCertificate() {
      if (this.sniffedCertificate == null) {
         logger.error("Sniffed certificate is null. Probably a network call using the sniffing verifier hasn't been made yet?");
         throw new IllegalStateException("Sniffed certificate not available.");
      } else {
         return this.sniffedCertificate;
      }
   }

   public String toString() {
      return this.getClass().getName() + this.hashCode();
   }
}
