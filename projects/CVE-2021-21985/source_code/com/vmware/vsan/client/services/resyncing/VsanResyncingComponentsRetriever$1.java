package com.vmware.vsan.client.services.resyncing;

import com.vmware.vsphere.client.vsan.base.data.ComponentIntent;

// $FF: synthetic class
class VsanResyncingComponentsRetriever$1 {
   // $FF: synthetic field
   static final int[] $SwitchMap$com$vmware$vsphere$client$vsan$base$data$ComponentIntent = new int[ComponentIntent.values().length];

   static {
      try {
         $SwitchMap$com$vmware$vsphere$client$vsan$base$data$ComponentIntent[ComponentIntent.REPAIR.ordinal()] = 1;
      } catch (NoSuchFieldError var8) {
      }

      try {
         $SwitchMap$com$vmware$vsphere$client$vsan$base$data$ComponentIntent[ComponentIntent.FIXCOMPLIANCE.ordinal()] = 2;
      } catch (NoSuchFieldError var7) {
      }

      try {
         $SwitchMap$com$vmware$vsphere$client$vsan$base$data$ComponentIntent[ComponentIntent.DECOM.ordinal()] = 3;
      } catch (NoSuchFieldError var6) {
      }

      try {
         $SwitchMap$com$vmware$vsphere$client$vsan$base$data$ComponentIntent[ComponentIntent.REBALANCE.ordinal()] = 4;
      } catch (NoSuchFieldError var5) {
      }

      try {
         $SwitchMap$com$vmware$vsphere$client$vsan$base$data$ComponentIntent[ComponentIntent.POLICYCHANGE.ordinal()] = 5;
      } catch (NoSuchFieldError var4) {
      }

      try {
         $SwitchMap$com$vmware$vsphere$client$vsan$base$data$ComponentIntent[ComponentIntent.MOVE.ordinal()] = 6;
      } catch (NoSuchFieldError var3) {
      }

      try {
         $SwitchMap$com$vmware$vsphere$client$vsan$base$data$ComponentIntent[ComponentIntent.STALE.ordinal()] = 7;
      } catch (NoSuchFieldError var2) {
      }

      try {
         $SwitchMap$com$vmware$vsphere$client$vsan$base$data$ComponentIntent[ComponentIntent.MERGE_CONTACT.ordinal()] = 8;
      } catch (NoSuchFieldError var1) {
      }

   }
}
