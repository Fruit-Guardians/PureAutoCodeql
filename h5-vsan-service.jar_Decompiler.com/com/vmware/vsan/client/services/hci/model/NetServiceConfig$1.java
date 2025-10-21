package com.vmware.vsan.client.services.hci.model;

// $FF: synthetic class
class NetServiceConfig$1 {
   // $FF: synthetic field
   static final int[] $SwitchMap$com$vmware$vsan$client$services$hci$model$NetServiceConfig$IpType = new int[NetServiceConfig.IpType.values().length];

   static {
      try {
         $SwitchMap$com$vmware$vsan$client$services$hci$model$NetServiceConfig$IpType[NetServiceConfig.IpType.DHCP.ordinal()] = 1;
      } catch (NoSuchFieldError var3) {
      }

      try {
         $SwitchMap$com$vmware$vsan$client$services$hci$model$NetServiceConfig$IpType[NetServiceConfig.IpType.ROUTER_ADVERTISEMENT.ordinal()] = 2;
      } catch (NoSuchFieldError var2) {
      }

      try {
         $SwitchMap$com$vmware$vsan$client$services$hci$model$NetServiceConfig$IpType[NetServiceConfig.IpType.STATIC.ordinal()] = 3;
      } catch (NoSuchFieldError var1) {
      }

   }
}
