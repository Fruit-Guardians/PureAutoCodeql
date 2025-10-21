package com.vmware.vsan.client.services.common.data;

import com.vmware.vim.binding.vim.TaskInfo.State;

// $FF: synthetic class
class TaskInfoData$1 {
   // $FF: synthetic field
   static final int[] $SwitchMap$com$vmware$vim$binding$vim$TaskInfo$State = new int[State.values().length];

   static {
      try {
         $SwitchMap$com$vmware$vim$binding$vim$TaskInfo$State[State.error.ordinal()] = 1;
      } catch (NoSuchFieldError var4) {
      }

      try {
         $SwitchMap$com$vmware$vim$binding$vim$TaskInfo$State[State.success.ordinal()] = 2;
      } catch (NoSuchFieldError var3) {
      }

      try {
         $SwitchMap$com$vmware$vim$binding$vim$TaskInfo$State[State.queued.ordinal()] = 3;
      } catch (NoSuchFieldError var2) {
      }

      try {
         $SwitchMap$com$vmware$vim$binding$vim$TaskInfo$State[State.running.ordinal()] = 4;
      } catch (NoSuchFieldError var1) {
      }

   }
}
