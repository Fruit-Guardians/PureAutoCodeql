package com.vmware.vsphere.client.vsan.iscsi.utils;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vsphere.client.vsan.util.Utils;

public class VsanIscsiUtil {
   public static final String iscsiEnableInProgressErrMsg = "vSAN iSCSI Target Service is not enabled or the enable task is in progress.";
   public static final String TASK_TYPE = "Task";

   public static String getLocalizedString(String key) {
      return Utils.getLocalizedString(key);
   }

   public static ManagedObjectReference buildTaskMor(String taskId, String vcGuid) {
      ManagedObjectReference task = new ManagedObjectReference("Task", taskId, vcGuid);
      return task;
   }
}
