package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;
import java.util.Date;

@data
public class PerfTimeRangeData extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public String name;
   public Date from;
   public Date to;
   public ManagedObjectReference clusterRef;
}
