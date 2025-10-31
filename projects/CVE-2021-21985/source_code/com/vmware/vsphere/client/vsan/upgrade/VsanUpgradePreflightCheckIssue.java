package com.vmware.vsphere.client.vsan.upgrade;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vise.core.model.data;

@data
public class VsanUpgradePreflightCheckIssue extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public String message;
   public VsanUpgradePreflightCheckIssue.IssueType type;

   @data
   public static enum IssueType {
      WARNING,
      ERROR;
   }
}
