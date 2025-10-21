package com.vmware.vsan.client.services.config;

import com.vmware.vsphere.client.vsan.data.EncryptionState;

// $FF: synthetic class
class VsanConfigService$1 {
   // $FF: synthetic field
   static final int[] $SwitchMap$com$vmware$vsphere$client$vsan$data$EncryptionState = new int[EncryptionState.values().length];

   static {
      try {
         $SwitchMap$com$vmware$vsphere$client$vsan$data$EncryptionState[EncryptionState.Enabled.ordinal()] = 1;
      } catch (NoSuchFieldError var3) {
      }

      try {
         $SwitchMap$com$vmware$vsphere$client$vsan$data$EncryptionState[EncryptionState.EnabledNoKmip.ordinal()] = 2;
      } catch (NoSuchFieldError var2) {
      }

      try {
         $SwitchMap$com$vmware$vsphere$client$vsan$data$EncryptionState[EncryptionState.Disabled.ordinal()] = 3;
      } catch (NoSuchFieldError var1) {
      }

   }
}
