package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vise.core.model.data;
import java.util.List;

@data
public class PerformanceDiagnosticData extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public List<DiagnosticException> issues;
   public List<String> entityRefIds;

   public PerformanceDiagnosticData() {
   }

   public PerformanceDiagnosticData(List<DiagnosticException> issues, List<String> entityRefIds) {
      this.issues = issues;
      this.entityRefIds = entityRefIds;
   }
}
