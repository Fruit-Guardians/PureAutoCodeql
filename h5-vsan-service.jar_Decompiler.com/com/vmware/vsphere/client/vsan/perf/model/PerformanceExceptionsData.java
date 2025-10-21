package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfEntityType;
import com.vmware.vise.core.model.data;
import java.util.Map;

@data
public class PerformanceExceptionsData extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public Map<String, PerformanceDiagnosticException> performanceExceptionIdToException;
   public Map<String, VsanPerfEntityType> performanceEntityTypes;
   public Map<String, VsanPerfEntityType> performanceAggregatedEntityTypes;
}
