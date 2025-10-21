package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso;

import com.vmware.vim.sso.client.util.codec.Base64;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class CertificateHelper {
   protected static CertificateFactory cf = getFactory();
   private static final String HEX = "0123456789ABCDEF";
   public static final String MD_ALGO = "SHA-1";

   protected static CertificateFactory getFactory() {
      try {
         return CertificateFactory.getInstance("X.509");
      } catch (CertificateException var1) {
         throw new RuntimeException(var1);
      }
   }

   public static X509Certificate pem2cert(String pem) throws CertificateException {
      return (X509Certificate)cf.generateCertificate(new ByteArrayInputStream(Base64.decodeBase64(pem)));
   }

   public static X509Certificate[] getCerts(String[] certs) throws CertificateException {
      List<X509Certificate> result = new ArrayList();
      String[] var5 = certs;
      int var4 = certs.length;

      for(int var3 = 0; var3 < var4; ++var3) {
         String pem = var5[var3];
         result.add(pem2cert(pem));
      }

      return (X509Certificate[])result.toArray(new X509Certificate[result.size()]);
   }

   public static String calcThumbprint(byte[] cert) throws NoSuchAlgorithmException {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] digest = md.digest(cert);
      StringBuilder thumbprint = new StringBuilder();
      int i = 0;

      for(int len = digest.length; i < len; ++i) {
         if (i > 0) {
            thumbprint.append(':');
         }

         byte b = digest[i];
         thumbprint.append("0123456789ABCDEF".charAt((b & 240) >> 4));
         thumbprint.append("0123456789ABCDEF".charAt(b & 15));
      }

      return thumbprint.toString();
   }
}
