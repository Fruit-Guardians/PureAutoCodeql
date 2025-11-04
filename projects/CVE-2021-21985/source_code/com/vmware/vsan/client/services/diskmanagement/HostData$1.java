package com.vmware.vsan.client.services.diskmanagement;

import com.vmware.vim.binding.vim.vsan.host.HealthState;
import com.vmware.vim.binding.vim.vsan.host.DiskResult.State;

// $FF: synthetic class
class HostData$1 {
   // $FF: synthetic field
   static final int[] $SwitchMap$com$vmware$vim$binding$vim$vsan$host$DiskResult$State;
   // $FF: synthetic field
   static final int[] $SwitchMap$com$vmware$vim$binding$vim$vsan$host$HealthState = new int[HealthState.values().length];

   static {
      try {
         $SwitchMap$com$vmware$vim$binding$vim$vsan$host$HealthState[HealthState.healthy.ordinal()] = 1;
      } catch (NoSuchFieldError var5) {
      }

      try {
         $SwitchMap$com$vmware$vim$binding$vim$vsan$host$HealthState[HealthState.unhealthy.ordinal()] = 2;
      } catch (NoSuchFieldError var4) {
      }

      $SwitchMap$com$vmware$vim$binding$vim$vsan$host$DiskResult$State = new int[State.values().length];

      try {
         $SwitchMap$com$vmware$vim$binding$vim$vsan$host$DiskResult$State[State.ineligible.ordinal()] = 1;
      } catch (NoSuchFieldError var3) {
      }

      try {
         $SwitchMap$com$vmware$vim$binding$vim$vsan$host$DiskResult$State[State.eligible.ordinal()] = 2;
      } catch (NoSuchFieldError var2) {
      }

      try {
         $SwitchMap$com$vmware$vim$binding$vim$vsan$host$DiskResult$State[State.inUse.ordinal()] = 3;
      } catch (NoSuchFieldError var1) {
      }

   }
}
