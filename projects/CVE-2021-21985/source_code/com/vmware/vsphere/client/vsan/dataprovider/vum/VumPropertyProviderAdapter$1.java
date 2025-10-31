package com.vmware.vsphere.client.vsan.dataprovider.vum;

import com.vmware.vsphere.client.vsan.data.VumBaselineRecommendationType;

// $FF: synthetic class
class VumPropertyProviderAdapter$1 {
   // $FF: synthetic field
   static final int[] $SwitchMap$com$vmware$vsphere$client$vsan$data$VumBaselineRecommendationType = new int[VumBaselineRecommendationType.values().length];

   static {
      try {
         $SwitchMap$com$vmware$vsphere$client$vsan$data$VumBaselineRecommendationType[VumBaselineRecommendationType.latestRelease.ordinal()] = 1;
      } catch (NoSuchFieldError var3) {
      }

      try {
         $SwitchMap$com$vmware$vsphere$client$vsan$data$VumBaselineRecommendationType[VumBaselineRecommendationType.latestPatch.ordinal()] = 2;
      } catch (NoSuchFieldError var2) {
      }

      try {
         $SwitchMap$com$vmware$vsphere$client$vsan$data$VumBaselineRecommendationType[VumBaselineRecommendationType.noRecommendation.ordinal()] = 3;
      } catch (NoSuchFieldError var1) {
      }

   }
}
