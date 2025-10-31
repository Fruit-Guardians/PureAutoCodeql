package com.vmware.vsan.client.services.hci;

import com.vmware.vsan.client.services.hci.model.HciWorkflowState;
import com.vmware.vsan.client.services.hci.model.Service;
import com.vmware.vsphere.client.vsan.data.EncryptionState;

// $FF: synthetic class
class HciClusterService$2 {
   // $FF: synthetic field
   static final int[] $SwitchMap$com$vmware$vsan$client$services$hci$model$HciWorkflowState;
   // $FF: synthetic field
   static final int[] $SwitchMap$com$vmware$vsphere$client$vsan$data$EncryptionState;
   // $FF: synthetic field
   static final int[] $SwitchMap$com$vmware$vsan$client$services$hci$model$Service = new int[Service.values().length];

   static {
      try {
         $SwitchMap$com$vmware$vsan$client$services$hci$model$Service[Service.VMOTION.ordinal()] = 1;
      } catch (NoSuchFieldError var8) {
      }

      try {
         $SwitchMap$com$vmware$vsan$client$services$hci$model$Service[Service.VSAN.ordinal()] = 2;
      } catch (NoSuchFieldError var7) {
      }

      $SwitchMap$com$vmware$vsphere$client$vsan$data$EncryptionState = new int[EncryptionState.values().length];

      try {
         $SwitchMap$com$vmware$vsphere$client$vsan$data$EncryptionState[EncryptionState.Enabled.ordinal()] = 1;
      } catch (NoSuchFieldError var6) {
      }

      try {
         $SwitchMap$com$vmware$vsphere$client$vsan$data$EncryptionState[EncryptionState.EnabledNoKmip.ordinal()] = 2;
      } catch (NoSuchFieldError var5) {
      }

      try {
         $SwitchMap$com$vmware$vsphere$client$vsan$data$EncryptionState[EncryptionState.Disabled.ordinal()] = 3;
      } catch (NoSuchFieldError var4) {
      }

      $SwitchMap$com$vmware$vsan$client$services$hci$model$HciWorkflowState = new int[HciWorkflowState.values().length];

      try {
         $SwitchMap$com$vmware$vsan$client$services$hci$model$HciWorkflowState[HciWorkflowState.IN_PROGRESS.ordinal()] = 1;
      } catch (NoSuchFieldError var3) {
      }

      try {
         $SwitchMap$com$vmware$vsan$client$services$hci$model$HciWorkflowState[HciWorkflowState.DONE.ordinal()] = 2;
      } catch (NoSuchFieldError var2) {
      }

      try {
         $SwitchMap$com$vmware$vsan$client$services$hci$model$HciWorkflowState[HciWorkflowState.INVALID.ordinal()] = 3;
      } catch (NoSuchFieldError var1) {
      }

   }
}
