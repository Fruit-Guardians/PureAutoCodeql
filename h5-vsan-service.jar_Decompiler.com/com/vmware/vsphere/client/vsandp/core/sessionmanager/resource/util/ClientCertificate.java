package com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util;

import com.vmware.vim.sso.client.util.codec.Base64;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStore.ProtectionParameter;
import java.security.KeyStore.TrustedCertificateEntry;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

public class ClientCertificate {
   protected final String keystorePath;
   protected final String keystorePass;
   protected final String keyPass;
   protected final String keystoreAlias;
   protected final KeyStore keystore;

   public ClientCertificate(String keystorePath, String keystorePass, String keyPass, String keystoreType, String keystoreAlias) {
      this.keystorePath = keystorePath;
      this.keystore = load(keystorePath, keystorePass, keystoreType);
      this.keystorePass = keystorePass;
      this.keyPass = keyPass;
      this.keystoreAlias = keystoreAlias;
   }

   public ClientCertificate(String keystorePath, KeyStore keystore, String keystorePass, String keyPass, String keystoreAlias) {
      this.keystorePath = keystorePath;
      this.keystore = keystore;
      this.keystorePass = keystorePass;
      this.keyPass = keyPass;
      this.keystoreAlias = keystoreAlias;
   }

   public ClientCertificate(String keystorePath, String[] certificates, String keystorePass, String keyPass, String keystoreAlias) {
      this.keystorePath = keystorePath;
      this.keystore = create(certificates);
      this.keystorePass = keystorePass;
      this.keyPass = keyPass;
      this.keystoreAlias = keystoreAlias;
   }

   public String getKeystorePath() {
      return this.keystorePath;
   }

   public KeyStore getKeystore() {
      return this.keystore;
   }

   public String getKeystorePass() {
      return this.keystorePass;
   }

   public String getKeyPass() {
      return this.keyPass;
   }

   public String getKeystoreAlias() {
      return this.keystoreAlias;
   }

   private static KeyStore load(String keystorePath, String keystorePass, String keystoreType) {
      boolean hasPass = keystorePass != null && !"".equals(keystorePass.trim());

      try {
         KeyStore keyStore = KeyStore.getInstance(keystoreType);
         keyStore.load(new FileInputStream(keystorePath), hasPass ? keystorePass.toCharArray() : null);
         return keyStore;
      } catch (Exception var5) {
         CheckedRunnable.handle(var5);
         return null;
      }
   }

   private static KeyStore create(String[] certificates) {
      try {
         CertificateFactory factory = CertificateFactory.getInstance("X.509");
         KeyStore keystore = KeyStore.getInstance("JKS");
         keystore.load((InputStream)null, (char[])null);

         for(int i = 0; i < certificates.length; ++i) {
            byte[] decoded = Base64.decodeBase64(certificates[i].replaceAll("-----BEGIN CERTIFICATE-----", "").replaceAll("-----END CERTIFICATE-----", ""));
            ByteArrayInputStream in = new ByteArrayInputStream(decoded);
            Certificate certificate = factory.generateCertificate(in);
            keystore.setEntry("Cert_" + i, new TrustedCertificateEntry(certificate), (ProtectionParameter)null);
         }

         return keystore;
      } catch (Exception var7) {
         CheckedRunnable.handle(var7);
         return null;
      }
   }

   public int hashCode() {
      boolean var10000 = true;
      int result = 1;
      int result = 31 * result + (this.keyPass == null ? 0 : this.keyPass.hashCode());
      result = 31 * result + (this.keystoreAlias == null ? 0 : this.keystoreAlias.hashCode());
      result = 31 * result + (this.keystorePass == null ? 0 : this.keystorePass.hashCode());
      result = 31 * result + (this.keystorePath == null ? 0 : this.keystorePath.hashCode());
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
         ClientCertificate other = (ClientCertificate)obj;
         if (this.keyPass == null) {
            if (other.keyPass != null) {
               return false;
            }
         } else if (!this.keyPass.equals(other.keyPass)) {
            return false;
         }

         if (this.keystoreAlias == null) {
            if (other.keystoreAlias != null) {
               return false;
            }
         } else if (!this.keystoreAlias.equals(other.keystoreAlias)) {
            return false;
         }

         if (this.keystorePass == null) {
            if (other.keystorePass != null) {
               return false;
            }
         } else if (!this.keystorePass.equals(other.keystorePass)) {
            return false;
         }

         if (this.keystorePath == null) {
            if (other.keystorePath != null) {
               return false;
            }
         } else if (!this.keystorePath.equals(other.keystorePath)) {
            return false;
         }

         return true;
      }
   }

   public String toString() {
      return String.format("ClientCertificate [keystorePath=%s, keystorePass=%s, keyPass=%s, keystoreAlias=%s]", this.keystorePath, this.keystorePass, this.keyPass, this.keystoreAlias);
   }
}
