package com.vmware.vsphere.client.vsan.upgrade;

import com.vmware.vim.binding.vim.VsanUpgradeSystem.PreflightCheckIssue;
import com.vmware.vim.binding.vim.VsanUpgradeSystem.UpgradeHistoryItem;
import com.vmware.vim.binding.vim.VsanUpgradeSystem.UpgradeStatus;
import com.vmware.vim.vsan.binding.vim.cluster.VsanUpgradeStatusEx;
import com.vmware.vise.core.model.data;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@data
public class VsanUpgradeStatusData {
   public boolean isAsyncPrecheckSupported;
   public Boolean isPrecheck = false;
   public Boolean aborted = false;
   public Boolean completed = false;
   public Boolean inProgress = false;
   public List<String> issues;
   public int progress;
   public String currentOperationName;
   public Date lastOperationDate;

   public VsanUpgradeStatusData() {
   }

   public VsanUpgradeStatusData(Boolean isAsyncPrecheckSupported) {
      this.isAsyncPrecheckSupported = isAsyncPrecheckSupported;
   }

   public VsanUpgradeStatusData(VsanUpgradeStatusEx statusEx) {
      this.isAsyncPrecheckSupported = true;
      this.isPrecheck = statusEx.isPrecheck;
      this.aborted = statusEx.aborted;
      this.completed = statusEx.completed;
      this.inProgress = statusEx.inProgress;
      this.progress = statusEx.progress;
      if (statusEx.precheckResult != null && statusEx.precheckResult.issues != null) {
         this.issues = new ArrayList();
         PreflightCheckIssue[] var5;
         int var4 = (var5 = statusEx.precheckResult.issues).length;

         for(int var3 = 0; var3 < var4; ++var3) {
            PreflightCheckIssue issue = var5[var3];
            this.issues.add(issue.msg);
         }
      }

      this.populateLastOperationNameAndTime(statusEx.history);
   }

   public VsanUpgradeStatusData(UpgradeStatus upgradeStatus) {
      this.isAsyncPrecheckSupported = false;
      this.isPrecheck = false;
      this.aborted = upgradeStatus.aborted;
      this.completed = upgradeStatus.completed;
      this.inProgress = upgradeStatus.inProgress;
      this.progress = upgradeStatus.progress;
      this.populateLastOperationNameAndTime(upgradeStatus.history);
   }

   private void populateLastOperationNameAndTime(UpgradeHistoryItem[] history) {
      if (history != null && history.length != 0) {
         Calendar lastTimestamp = null;
         String lastOperation = "";
         UpgradeHistoryItem[] var7 = history;
         int var6 = history.length;

         for(int var5 = 0; var5 < var6; ++var5) {
            UpgradeHistoryItem historyItem = var7[var5];
            if (lastTimestamp == null || lastTimestamp.compareTo(historyItem.timestamp) < 0) {
               lastTimestamp = historyItem.timestamp;
               lastOperation = historyItem.message;
            }
         }

         if (lastTimestamp != null) {
            this.lastOperationDate = lastTimestamp.getTime();
         }

         if (this.inProgress) {
            this.currentOperationName = lastOperation;
         }

      }
   }
}
