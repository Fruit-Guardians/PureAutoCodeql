package com.vmware.vsphere.client.vsan.base.data;

import com.vmware.vim.vsandp.binding.vim.vsandp.ProtectionState;

// $FF: synthetic class
class VsanObjectDataProtectionHealthState$1 {
   // $FF: synthetic field
   static final int[] $SwitchMap$com$vmware$vim$vsandp$binding$vim$vsandp$ProtectionState = new int[ProtectionState.values().length];

   static {
      try {
         $SwitchMap$com$vmware$vim$vsandp$binding$vim$vsandp$ProtectionState[ProtectionState.healthOk.ordinal()] = 1;
      } catch (NoSuchFieldError var14) {
      }

      try {
         $SwitchMap$com$vmware$vim$vsandp$binding$vim$vsandp$ProtectionState[ProtectionState.unknown.ordinal()] = 2;
      } catch (NoSuchFieldError var13) {
      }

      try {
         $SwitchMap$com$vmware$vim$vsandp$binding$vim$vsandp$ProtectionState[ProtectionState.protectionNotConfigured.ordinal()] = 3;
      } catch (NoSuchFieldError var12) {
      }

      try {
         $SwitchMap$com$vmware$vim$vsandp$binding$vim$vsandp$ProtectionState[ProtectionState.fullSyncInProgress.ordinal()] = 4;
      } catch (NoSuchFieldError var11) {
      }

      try {
         $SwitchMap$com$vmware$vim$vsandp$binding$vim$vsandp$ProtectionState[ProtectionState.protectionNotOwner.ordinal()] = 5;
      } catch (NoSuchFieldError var10) {
      }

      try {
         $SwitchMap$com$vmware$vim$vsandp$binding$vim$vsandp$ProtectionState[ProtectionState.vmQuiescingFailure.ordinal()] = 6;
      } catch (NoSuchFieldError var9) {
      }

      try {
         $SwitchMap$com$vmware$vim$vsandp$binding$vim$vsandp$ProtectionState[ProtectionState.invalidConfiguration.ordinal()] = 7;
      } catch (NoSuchFieldError var8) {
      }

      try {
         $SwitchMap$com$vmware$vim$vsandp$binding$vim$vsandp$ProtectionState[ProtectionState.archiveStorageNotAccessible.ordinal()] = 8;
      } catch (NoSuchFieldError var7) {
      }

      try {
         $SwitchMap$com$vmware$vim$vsandp$binding$vim$vsandp$ProtectionState[ProtectionState.archiveStorageNoSpace.ordinal()] = 9;
      } catch (NoSuchFieldError var6) {
      }

      try {
         $SwitchMap$com$vmware$vim$vsandp$binding$vim$vsandp$ProtectionState[ProtectionState.archiveTargetNotConfigured.ordinal()] = 10;
      } catch (NoSuchFieldError var5) {
      }

      try {
         $SwitchMap$com$vmware$vim$vsandp$binding$vim$vsandp$ProtectionState[ProtectionState.cgObjectUnavailable.ordinal()] = 11;
      } catch (NoSuchFieldError var4) {
      }

      try {
         $SwitchMap$com$vmware$vim$vsandp$binding$vim$vsandp$ProtectionState[ProtectionState.localRetentionFailure.ordinal()] = 12;
      } catch (NoSuchFieldError var3) {
      }

      try {
         $SwitchMap$com$vmware$vim$vsandp$binding$vim$vsandp$ProtectionState[ProtectionState.localStorageUsageExceededThreshold.ordinal()] = 13;
      } catch (NoSuchFieldError var2) {
      }

      try {
         $SwitchMap$com$vmware$vim$vsandp$binding$vim$vsandp$ProtectionState[ProtectionState.containsUnpromotedObjects.ordinal()] = 14;
      } catch (NoSuchFieldError var1) {
      }

   }
}
