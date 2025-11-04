package com.vmware.vsan.client.services.encryption;

import com.vmware.vise.core.model.data;
import com.vmware.vsphere.client.vsan.data.EncryptionState;

@data
public class EncryptionStatus {
   public EncryptionState state;
   public String kmipClusterId;
   public Boolean eraseDisksBeforeUse;
}
