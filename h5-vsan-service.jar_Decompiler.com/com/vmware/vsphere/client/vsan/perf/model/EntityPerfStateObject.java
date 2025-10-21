package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfEntityMetricCSV;
import com.vmware.vise.core.model.data;

@data
public class EntityPerfStateObject {
   public String errorMessage;
   public VsanPerfEntityMetricCSV[] metrics;
}
