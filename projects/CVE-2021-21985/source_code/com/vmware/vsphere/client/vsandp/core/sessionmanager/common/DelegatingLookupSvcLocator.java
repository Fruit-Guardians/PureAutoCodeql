package com.vmware.vsphere.client.vsandp.core.sessionmanager.common;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.Arrays;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class DelegatingLookupSvcLocator implements LookupSvcLocator {
   private static final Log logger = LogFactory.getLog(DelegatingLookupSvcLocator.class);
   private final LookupSvcLocator[] availableLocators;

   public DelegatingLookupSvcLocator(LookupSvcLocator[] availableLocators) {
      this.availableLocators = availableLocators;
   }

   public LookupSvcInfo getInfo() {
      LookupSvcInfo result = null;
      LookupSvcLocator[] var5;
      int var4 = (var5 = this.availableLocators).length;
      int var3 = 0;

      while(var3 < var4) {
         LookupSvcLocator locator = var5[var3];
         logger.trace("Trying to obtain LookupSvcInfo from: " + locator);

         try {
            result = locator.getInfo();
            logger.trace("Successfully obtained LookupSvcInfo from " + locator + " => " + result);
            break;
         } catch (Throwable var7) {
            logger.trace("Failed to obtain LookupSvcInfo from " + locator, var7);
            ++var3;
         }
      }

      if (result != null) {
         return result;
      } else {
         logger.error("Could not obtain LookupSvcInfo, all locators failed: " + Arrays.toString(this.availableLocators));
         throw new IllegalStateException("Unable to obtain LookupSvcInfo from any locator.");
      }
   }

   public KeyStore getH5Keystore() {
      LookupSvcLocator[] var4;
      int var3 = (var4 = this.availableLocators).length;
      int var2 = 0;

      while(var2 < var3) {
         LookupSvcLocator locator = var4[var2];

         try {
            return locator.getH5Keystore();
         } catch (Throwable var6) {
            logger.trace("Failed to obtain H5 keystore from " + locator, var6);
            ++var2;
         }
      }

      throw new IllegalStateException("Unable to obtain H5 keystore from any locator.");
   }

   public PrivateKey getPrivateKey() {
      LookupSvcLocator[] var4;
      int var3 = (var4 = this.availableLocators).length;

      for(int var2 = 0; var2 < var3; ++var2) {
         LookupSvcLocator locator = var4[var2];

         try {
            PrivateKey key = locator.getPrivateKey();
            if (key != null) {
               return key;
            }
         } catch (Throwable var6) {
            logger.trace("Failed to obtain private key from " + locator, var6);
         }
      }

      throw new IllegalStateException("Unable to obtain PrivateKey from any locator.");
   }
}
