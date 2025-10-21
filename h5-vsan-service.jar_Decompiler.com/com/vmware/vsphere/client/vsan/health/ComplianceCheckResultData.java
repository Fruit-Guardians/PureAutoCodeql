package com.vmware.vsphere.client.vsan.health;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vise.core.model.data;

@data
public class ComplianceCheckResultData extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public ComplianceCheckSummary summary;
   public ComplianceCheckResultObj[] details;
}
