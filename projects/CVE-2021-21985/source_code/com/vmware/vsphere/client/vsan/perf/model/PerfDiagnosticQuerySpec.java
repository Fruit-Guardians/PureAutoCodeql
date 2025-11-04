package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vise.core.model.data;
import java.util.Calendar;

@data
public class PerfDiagnosticQuerySpec extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public Calendar startTime;
   public Calendar endTime;
   public PerfDiagnosticType queryType;
}
