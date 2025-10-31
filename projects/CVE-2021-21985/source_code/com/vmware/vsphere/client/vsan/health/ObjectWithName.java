package com.vmware.vsphere.client.vsan.health;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;

@data
public class ObjectWithName extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public String objectName;
   public ManagedObjectReference object;

   public ObjectWithName() {
   }

   public ObjectWithName(String objectName, ManagedObjectReference object) {
      this.objectName = objectName;
      this.object = object;
   }
}
