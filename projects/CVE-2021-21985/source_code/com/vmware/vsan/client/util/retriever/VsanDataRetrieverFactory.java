package com.vmware.vsan.client.util.retriever;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vsan.client.services.common.PermissionService;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.PbmClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VsanDataRetrieverFactory {
   @Autowired
   public VcClient vcClient;
   @Autowired
   public VmodlHelper vmodlHelper;
   @Autowired
   public PbmClient pbmClient;
   @Autowired
   private PermissionService permissionService;

   public VsanAsyncDataRetriever createVsanAsyncDataRetriever(Measure measure, ManagedObjectReference clusterRef) {
      return new VsanAsyncDataRetriever(measure, clusterRef, this.vcClient, this.vmodlHelper, this.pbmClient, this.permissionService);
   }

   public VsanAsyncDataRetriever createVsanAsyncDataRetriever(String name, ManagedObjectReference clusterRef) {
      return new VsanAsyncDataRetriever(new Measure(name), clusterRef, this.vcClient, this.vmodlHelper, this.pbmClient, this.permissionService);
   }
}
