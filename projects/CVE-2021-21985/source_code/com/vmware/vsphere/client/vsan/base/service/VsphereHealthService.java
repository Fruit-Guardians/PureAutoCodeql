package com.vmware.vsphere.client.vsan.base.service;

import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterHealthSystem;

public interface VsphereHealthService {
   VsanVcClusterHealthSystem getVsphereHealthSystem();

   void logout();
}
