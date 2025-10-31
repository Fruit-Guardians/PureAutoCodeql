package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http;

import com.vmware.vim.vmomi.client.http.ThumbprintVerifier;
import com.vmware.vim.vmomi.client.http.ThumbprintVerifier.Result;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Set;
import javax.net.ssl.SSLException;

public class ThumbprintSetVerifier implements ThumbprintVerifier {
   protected final Set<String> thumbprints;

   public ThumbprintSetVerifier(Set<String> thumbprints) {
      this.thumbprints = thumbprints;
   }

   public ThumbprintSetVerifier(String... strings) {
      this.thumbprints = new HashSet();
      String[] var5 = strings;
      int var4 = strings.length;

      for(int var3 = 0; var3 < var4; ++var3) {
         String thumbprint = var5[var3];
         this.thumbprints.add(thumbprint.toLowerCase());
      }

   }

   public Result verify(String thumbprint) {
      return this.thumbprints.contains(thumbprint.toLowerCase()) ? Result.MATCH : Result.MISMATCH;
   }

   public void onSuccess(X509Certificate[] chain, String thumbprint, Result verifyResult, boolean trustedChain, boolean verifiedAssertions) throws SSLException {
   }
}
