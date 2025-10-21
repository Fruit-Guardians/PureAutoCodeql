package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vise.core.model.data;

@data
public class DiagnosticIssueEntity extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public String recommendation;

   public DiagnosticIssueEntity() {
   }

   public DiagnosticIssueEntity(String recommendation) {
      this.recommendation = recommendation;
   }
}
