package com.vmware.vsphere.client.vsandp.core.sessionmanager.common;

import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.ClientCertificate;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FallbackLookupSvcLocator implements LookupSvcLocator {
   private static final String[] WEBCLIENT_PROPS = new String[]{"/etc/vmware/vsphere-client/vsphere-client/webclient.properties", "/var/lib/vmware/vsphere-client/vsphere-client/webclient.properties", "C:\\ProgramData\\VMware\\vCenterServer\\cfg\\vsphere-client\\webclient.properties"};
   private static final String CM_ADDRESS_PROPERTY = "cm.url";
   private static final String KEYSTORE_PATH = "keystore.jks.path";
   private static final String KEYSTORE_PASSWORD = "keystore.jks.password";
   private final Logger logger = LoggerFactory.getLogger(this.getClass());
   private String cmAddress;
   private String cmThumbprint;
   private KeyStore cmKeystore;
   private String keystorePass;

   private File getWebclientPropsFile() {
      String[] var4;
      int var3 = (var4 = WEBCLIENT_PROPS).length;

      for(int var2 = 0; var2 < var3; ++var2) {
         String potentionPath = var4[var2];
         File file = new File(potentionPath);
         if (file.isFile()) {
            return file;
         }
      }

      throw new IllegalStateException("webclient.properties not found: " + Arrays.toString(WEBCLIENT_PROPS));
   }

   private void retrieveCmProperties() throws Exception {
      File propertiesFile = this.getWebclientPropsFile();
      this.logger.debug("Client properties file is '{}'.", propertiesFile.getAbsolutePath());
      Properties properties = new Properties();

      Object keystorePath;
      try {
         Throwable var3 = null;
         keystorePath = null;

         try {
            FileInputStream in = new FileInputStream(propertiesFile);

            try {
               properties.load(in);
               this.logger.debug("Loaded properties from '{}'.", propertiesFile.getAbsolutePath());
            } finally {
               if (in != null) {
                  in.close();
               }

            }
         } catch (Throwable var13) {
            if (var3 == null) {
               var3 = var13;
            } else if (var3 != var13) {
               var3.addSuppressed(var13);
            }

            throw var3;
         }
      } catch (IOException var14) {
         throw new IllegalStateException("Failed to read properties: " + propertiesFile, var14);
      }

      String cmAddress = properties.getProperty("cm.url");
      if (cmAddress == null) {
         throw new IllegalStateException("Property 'cm.url' is missing from the local client configuration.");
      } else {
         this.logger.debug("Configured CM address is: {}", cmAddress);
         this.cmThumbprint = CertificateUtils.getServerThumbprint(cmAddress);
         this.logger.debug("Configured CM thumbprint is: {}", this.cmThumbprint);
         keystorePath = properties.get("keystore.jks.path");
         Object keystorePass = properties.get("keystore.jks.password");
         if (keystorePath != null) {
            this.cmKeystore = (new ClientCertificate(keystorePath.toString(), keystorePass.toString(), "", "JKS", "")).getKeystore();
         }

         this.cmAddress = cmAddress;
         this.keystorePass = keystorePass.toString();
      }
   }

   public LookupSvcInfo getInfo() {
      try {
         if (this.cmAddress == null) {
            this.retrieveCmProperties();
         }

         URI cmAddress = new URI(this.cmAddress);
         URI lsAddress = new URI("https", cmAddress.getHost(), "/lookupservice/sdk", (String)null);
         LookupSvcInfo result = (new LookupSvcInfo(lsAddress, this.cmThumbprint)).copyWithKeyStore(this.cmKeystore);
         this.logger.trace("Current LS is: {}", result);
         return result;
      } catch (Exception var4) {
         throw new IllegalStateException("Failed to retrieve current LookupSvcInfo.", var4);
      }
   }

   public PrivateKey getPrivateKey() {
      try {
         return (PrivateKey)this.getInfo().getKeyStore().getKey("vsphere-webclient", this.keystorePass.toCharArray());
      } catch (Exception var2) {
         throw new IllegalStateException("Failed to extract private key from JKS.", var2);
      }
   }

   public KeyStore getH5Keystore() {
      return this.getInfo().getKeyStore();
   }
}
