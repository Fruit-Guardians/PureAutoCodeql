package com.vmware.vsan.client.services.hci.model;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;

@data
public class HostInCluster {
   public ManagedObjectReference moRef;
   public String hostUid;
   public String name;

   public static HostInCluster create(ManagedObjectReference moRef, String hostUid, String name) {
      HostInCluster result = new HostInCluster();
      result.moRef = moRef;
      result.hostUid = hostUid;
      result.name = name;
      return result;
   }
}
