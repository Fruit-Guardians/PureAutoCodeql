package com.vmware.vsan.client.services.capability;

// $FF: synthetic class
class VsanCapabilityCacheManager$1 {
   // $FF: synthetic field
   static final int[] $SwitchMap$com$vmware$vsan$client$services$capability$VsanCapabilityCacheManager$VsanCacheType = new int[VsanCapabilityCacheManager.VsanCacheType.values().length];

   static {
      try {
         $SwitchMap$com$vmware$vsan$client$services$capability$VsanCapabilityCacheManager$VsanCacheType[VsanCapabilityCacheManager.VsanCacheType.VC.ordinal()] = 1;
      } catch (NoSuchFieldError var3) {
      }

      try {
         $SwitchMap$com$vmware$vsan$client$services$capability$VsanCapabilityCacheManager$VsanCacheType[VsanCapabilityCacheManager.VsanCacheType.CLUSTER.ordinal()] = 2;
      } catch (NoSuchFieldError var2) {
      }

      try {
         $SwitchMap$com$vmware$vsan$client$services$capability$VsanCapabilityCacheManager$VsanCacheType[VsanCapabilityCacheManager.VsanCacheType.HOST.ordinal()] = 3;
      } catch (NoSuchFieldError var1) {
      }

   }
}
