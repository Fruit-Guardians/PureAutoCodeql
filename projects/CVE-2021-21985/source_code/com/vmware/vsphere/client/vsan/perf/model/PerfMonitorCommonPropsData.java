package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfEntityType;
import com.vmware.vise.core.model.data;
import java.util.Map;

@data
public class PerfMonitorCommonPropsData {
   public ManagedObjectReference clusterRef;
   public Long currentTimeOnMasterNode;
   public Map<String, VsanPerfEntityType> entityTypes;
   public boolean isPerformanceServiceEnabled;
   public boolean isVerboseModeEnabled;
   public boolean hasEditPrivilege;
   public boolean isDataProtectionSupported;
   public boolean isIscsiServiceEnabled;
   public boolean isFileServiceEnabled;
   public boolean isEmptyClusterForIscsi;
}
