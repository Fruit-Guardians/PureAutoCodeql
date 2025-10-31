package com.vmware.vsan.client.services.common.data;

import com.vmware.vim.binding.vim.TaskInfo;
import com.vmware.vim.binding.vim.TaskInfo.State;
import com.vmware.vise.core.model.data;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

@data
public class TaskInfoData {
   private static final Log _logger = LogFactory.getLog(TaskInfoData.class);
   public String status;
   public Object result;
   public String exception;
   public int progress;
   // $FF: synthetic field
   private static int[] $SWITCH_TABLE$com$vmware$vim$binding$vim$TaskInfo$State;

   public static TaskInfoData fromTaskInfo(TaskInfo taskInfo) {
      Validate.notNull(taskInfo);
      TaskInfoData taskInfoData = new TaskInfoData();
      taskInfoData.status = taskInfo.state.toString();
      taskInfoData.result = taskInfo.result;
      if (taskInfo.error != null) {
         taskInfoData.exception = taskInfo.error.getLocalizedMessage();
      }

      Integer progress = taskInfo.progress;
      if (progress != null) {
         taskInfoData.progress = progress;
      } else {
         switch($SWITCH_TABLE$com$vmware$vim$binding$vim$TaskInfo$State()[taskInfo.state.ordinal()]) {
         case 1:
            taskInfoData.progress = 0;
         case 2:
            _logger.warn("Strange... the task's state is 'running' but the progress is not set.");
            taskInfoData.progress = 0;
            break;
         case 3:
         case 4:
            taskInfoData.progress = 100;
            break;
         default:
            _logger.warn("Unknown TaskInfo.state: " + taskInfo.state);
         }
      }

      return taskInfoData;
   }

   // $FF: synthetic method
   static int[] $SWITCH_TABLE$com$vmware$vim$binding$vim$TaskInfo$State() {
      int[] var10000 = $SWITCH_TABLE$com$vmware$vim$binding$vim$TaskInfo$State;
      if (var10000 != null) {
         return var10000;
      } else {
         int[] var0 = new int[State.values().length];

         try {
            var0[State.error.ordinal()] = 4;
         } catch (NoSuchFieldError var4) {
         }

         try {
            var0[State.queued.ordinal()] = 1;
         } catch (NoSuchFieldError var3) {
         }

         try {
            var0[State.running.ordinal()] = 2;
         } catch (NoSuchFieldError var2) {
         }

         try {
            var0[State.success.ordinal()] = 3;
         } catch (NoSuchFieldError var1) {
         }

         $SWITCH_TABLE$com$vmware$vim$binding$vim$TaskInfo$State = var0;
         return var0;
      }
   }
}
