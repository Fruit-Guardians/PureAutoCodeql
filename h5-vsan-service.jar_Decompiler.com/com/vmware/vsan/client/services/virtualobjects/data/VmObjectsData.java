package com.vmware.vsan.client.services.virtualobjects.data;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;
import com.vmware.vsan.client.services.common.data.VmData;
import com.vmware.vsphere.client.vsan.base.data.VsanObject;
import java.util.ArrayList;
import java.util.List;

@data
public class VmObjectsData {
   public ManagedObjectReference vmRef;
   public String name;
   public String primaryIconId;
   public List<VsanObject> vmObjects;

   public VmObjectsData() {
   }

   public VmObjectsData(VmData vmData) {
      this.vmRef = vmData.vmRef;
      this.name = vmData.name;
      this.primaryIconId = vmData.primaryIconId;
      this.vmObjects = new ArrayList();
   }
}
