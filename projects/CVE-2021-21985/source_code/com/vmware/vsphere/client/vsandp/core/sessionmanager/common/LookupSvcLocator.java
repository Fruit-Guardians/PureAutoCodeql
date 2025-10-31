package com.vmware.vsphere.client.vsandp.core.sessionmanager.common;

import java.security.KeyStore;
import java.security.PrivateKey;

public interface LookupSvcLocator {
   String KEYSTORE_JKS = "JKS";
   String KEYSTORE_VECS = "VKS";
   String H5_ALIAS = "vsphere-webclient";
   String TRUSTED_ROOTS_ALIAS = "TRUSTED_ROOTS";

   LookupSvcInfo getInfo();

   KeyStore getH5Keystore();

   PrivateKey getPrivateKey();
}
