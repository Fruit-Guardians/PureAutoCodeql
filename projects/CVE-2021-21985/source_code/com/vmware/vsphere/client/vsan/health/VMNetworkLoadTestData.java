package com.vmware.vsphere.client.vsan.health;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vise.core.model.data;

@data
public class VMNetworkLoadTestData extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public String hostName;
   public boolean client;
   public long bandWidth;
   public long totalBytes;
}
