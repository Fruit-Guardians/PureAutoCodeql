package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfEntityType;
import com.vmware.vise.core.model.data;
import java.util.Map;

@data
public class CapacityHistoryBasicInfo {
   public Map<String, VsanPerfEntityType> entityTypes;
   public boolean isPerformanceServiceEnabled;
   public boolean hasEditPolicyPermission;
   public ManagedObjectReference clusterRef;
   public boolean isLocalDataProtectionSupported;
   public boolean isEmptyCluster;
}
