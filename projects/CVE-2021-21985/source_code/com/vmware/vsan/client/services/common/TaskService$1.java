package com.vmware.vsan.client.services.common;

import com.vmware.vim.binding.vim.TaskInfo.State;

// $FF: synthetic class
class TaskService$1 {
   // $FF: synthetic field
   static final int[] $SwitchMap$com$vmware$vim$binding$vim$TaskInfo$State = new int[State.values().length];

   static {
      try {
         $SwitchMap$com$vmware$vim$binding$vim$TaskInfo$State[State.success.ordinal()] = 1;
      } catch (NoSuchFieldError var2) {
      }

      try {
         $SwitchMap$com$vmware$vim$binding$vim$TaskInfo$State[State.error.ordinal()] = 2;
      } catch (NoSuchFieldError var1) {
      }

   }
}
