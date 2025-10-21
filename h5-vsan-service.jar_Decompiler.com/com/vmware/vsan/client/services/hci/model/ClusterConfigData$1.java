package com.vmware.vsan.client.services.hci.model;

// $FF: synthetic class
class ClusterConfigData$1 {
   // $FF: synthetic field
   static final int[] $SwitchMap$com$vmware$vsan$client$services$hci$model$DrsAutoLevel = new int[DrsAutoLevel.values().length];

   static {
      try {
         $SwitchMap$com$vmware$vsan$client$services$hci$model$DrsAutoLevel[DrsAutoLevel.FULLY_AUTOMATED.ordinal()] = 1;
      } catch (NoSuchFieldError var3) {
      }

      try {
         $SwitchMap$com$vmware$vsan$client$services$hci$model$DrsAutoLevel[DrsAutoLevel.MANUAL.ordinal()] = 2;
      } catch (NoSuchFieldError var2) {
      }

      try {
         $SwitchMap$com$vmware$vsan$client$services$hci$model$DrsAutoLevel[DrsAutoLevel.PARTIALLY_AUTOMATED.ordinal()] = 3;
      } catch (NoSuchFieldError var1) {
      }

   }
}
