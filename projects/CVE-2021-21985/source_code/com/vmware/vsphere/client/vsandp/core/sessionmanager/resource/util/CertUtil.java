package com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStore.ProtectionParameter;
import java.security.KeyStore.TrustedCertificateEntry;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

public class CertUtil {
   public static final String BEGIN_CERT = "-----BEGIN CERTIFICATE-----";
   public static final String END_CERT = "-----END CERTIFICATE-----";

   public static KeyStore create(String... certificates) {
      try {
         KeyStore keystore = KeyStore.getInstance("JKS");
         keystore.load((InputStream)null, (char[])null);

         for(int i = 0; i < certificates.length; ++i) {
            Certificate certificate = parseX509(certificates[i]);
            keystore.setEntry("Cert_" + i, new TrustedCertificateEntry(certificate), (ProtectionParameter)null);
         }

         return keystore;
      } catch (RuntimeException var4) {
         throw var4;
      } catch (Exception var5) {
         throw new RuntimeException(var5);
      }
   }

   public static String extractCert(String cert) {
      int idx = cert.indexOf("-----BEGIN CERTIFICATE-----");
      if (idx >= 0) {
         cert = cert.substring(idx + "-----BEGIN CERTIFICATE-----".length());
      }

      idx = cert.indexOf("-----END CERTIFICATE-----");
      if (idx >= 0) {
         cert = cert.substring(0, idx);
      }

      cert = cert.trim().replace("\n", "").replace("\r", "");
      return cert;
   }

   public static X509Certificate parseX509(String param0) {
      // $FF: Couldn't be decompiled
   }
}
