package com.vmware.vsphere.client.vsan.data;

import com.vmware.vise.core.model.data;

@data
public class EncryptionRekeySpec {
   private static final long serialVersionUID = 1L;
   public boolean reEncryptData;
   public boolean allowReducedRedundancy;
}
