package com.vmware.vsphere.client.vsan.data;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vise.core.model.data;
import java.util.ArrayList;
import java.util.List;

@data
public class KmipClusterData extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public List<String> availableKmipClusters = new ArrayList();
   public String defaultKmipCluster;
   public boolean hasManageKeyServersPermissions;
}
