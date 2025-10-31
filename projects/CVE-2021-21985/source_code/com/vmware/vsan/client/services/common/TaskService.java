package com.vmware.vsan.client.services.common;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.Task;
import com.vmware.vim.binding.vim.TaskInfo;
import com.vmware.vim.binding.vim.TaskInfo.State;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vsan.client.services.common.data.TaskInfoData;
import com.vmware.vsphere.client.vsan.util.Utils;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import java.util.concurrent.TimeoutException;
import org.apache.commons.lang.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskService {
   private Logger logger = LoggerFactory.getLogger(TaskService.class);
   private static final int MAX_TRIES = 100;
   private static final int POLL_DELAY = 1000;
   @Autowired
   private VcClient vcClient;
   // $FF: synthetic field
   private static int[] $SWITCH_TABLE$com$vmware$vim$binding$vim$TaskInfo$State;

   public Object getResult(ManagedObjectReference taskRef) throws TimeoutException, InterruptedException {
      return this.getResult(taskRef, 100, 1000);
   }

   public Object getResult(ManagedObjectReference taskRef, int retries, int delay) throws TimeoutException, InterruptedException {
      Validate.notNull(taskRef);
      Throwable var4 = null;
      Object var5 = null;

      try {
         VcConnection vcConnection = this.vcClient.getConnection(taskRef.getServerGuid());

         try {
            Task task = (Task)vcConnection.createStub(Task.class, taskRef);
            int i = 0;

            while(i < retries) {
               TaskInfo taskInfo = task.getInfo();
               switch($SWITCH_TABLE$com$vmware$vim$binding$vim$TaskInfo$State()[taskInfo.getState().ordinal()]) {
               case 3:
                  Object var16 = taskInfo.getResult();
                  return var16;
               case 4:
                  Exception var10000 = taskInfo.getError();
                  return var10000;
               default:
                  Thread.sleep((long)delay);
                  ++i;
               }
            }

            throw new TimeoutException(Utils.getLocalizedString("vsan.task.timeout"));
         } finally {
            if (vcConnection != null) {
               vcConnection.close();
            }

         }
      } catch (Throwable var15) {
         if (var4 == null) {
            var4 = var15;
         } else if (var4 != var15) {
            var4.addSuppressed(var15);
         }

         throw var4;
      }
   }

   @TsService
   public TaskInfoData getInfo(ManagedObjectReference taskRef) {
      Validate.notNull(taskRef);
      TaskInfo taskInfo = this.getTaskInfo(taskRef);
      return TaskInfoData.fromTaskInfo(taskInfo);
   }

   private TaskInfo getTaskInfo(ManagedObjectReference taskRef) {
      Throwable var2 = null;
      Object var3 = null;

      try {
         VcConnection vcConnection = this.vcClient.getConnection(taskRef.getServerGuid());

         Throwable var10000;
         label173: {
            boolean var10001;
            TaskInfo var18;
            try {
               Task task = (Task)vcConnection.createStub(Task.class, taskRef);
               var18 = task.getInfo();
            } catch (Throwable var16) {
               var10000 = var16;
               var10001 = false;
               break label173;
            }

            if (vcConnection != null) {
               vcConnection.close();
            }

            label162:
            try {
               return var18;
            } catch (Throwable var15) {
               var10000 = var15;
               var10001 = false;
               break label162;
            }
         }

         var2 = var10000;
         if (vcConnection != null) {
            vcConnection.close();
         }

         throw var2;
      } catch (Throwable var17) {
         if (var2 == null) {
            var2 = var17;
         } else if (var2 != var17) {
            var2.addSuppressed(var17);
         }

         throw var2;
      }
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
