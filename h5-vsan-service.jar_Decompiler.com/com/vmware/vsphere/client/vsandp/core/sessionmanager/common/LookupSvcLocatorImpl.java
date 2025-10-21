package com.vmware.vsphere.client.vsandp.core.sessionmanager.common;

import com.vmware.af.VmAfClient;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.KeyStore.LoadStoreParameter;
import java.security.cert.CertificateException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class LookupSvcLocatorImpl implements LookupSvcLocator {
   private static final Log logger = LogFactory.getLog(LookupSvcLocatorImpl.class);
   private VmAfClient afClient;
   private KeyStore vecsKeystore;

   public LookupSvcInfo getInfo() {
      try {
         KeyStore keyStore = this.getKeyStore();
         URI address = URI.create(this.getAfClient().getLSLocation());
         return new LookupSvcInfo(address, keyStore);
      } catch (Exception var3) {
         throw new IllegalStateException("Could not find LS info from AFD/VECS", var3);
      }
   }

   public PrivateKey getPrivateKey() {
      try {
         KeyStore keyStore = KeyStore.getInstance("VKS");
         keyStore.load(this.getLoader("vsphere-webclient"));
         Key key = keyStore.getKey("vsphere-webclient", (char[])null);
         return key instanceof PrivateKey ? (PrivateKey)key : null;
      } catch (Exception var3) {
         throw new IllegalStateException("Failed to acquire private key.", var3);
      }
   }

   private VmAfClient getAfClient() {
      if (this.afClient == null) {
         this.afClient = new VmAfClient("localhost");
      }

      return this.afClient;
   }

   private KeyStore getKeyStore() throws KeyStoreException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException, CertificateException, NoSuchAlgorithmException, IOException {
      if (this.vecsKeystore == null) {
         this.vecsKeystore = KeyStore.getInstance("VKS");
         this.vecsKeystore.load(this.getLoader("TRUSTED_ROOTS"));
         logger.info("VECS keystore loaded: " + this.vecsKeystore);
      }

      return this.vecsKeystore;
   }

   private LoadStoreParameter getLoader(String alias) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
      Class<? extends LoadStoreParameter> loaderClass = Class.forName("com.vmware.provider.VecsLoadStoreParameter");
      LoadStoreParameter loader = (LoadStoreParameter)loaderClass.getConstructor(String.class).newInstance(alias);
      return loader;
   }

   public KeyStore getH5Keystore() {
      try {
         KeyStore keyStore = KeyStore.getInstance("VKS");
         keyStore.load(this.getLoader("vsphere-webclient"));
         return keyStore;
      } catch (Exception var2) {
         throw new IllegalStateException("Failed to acquire private key.", var2);
      }
   }
}
