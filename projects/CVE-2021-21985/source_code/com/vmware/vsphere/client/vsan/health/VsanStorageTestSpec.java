package com.vmware.vsphere.client.vsan.health;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vim.vsan.binding.vim.cluster.VsanStorageWorkloadType;
import com.vmware.vise.core.model.data;

@data
public class VsanStorageTestSpec extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public String typeId;
   public String name;
   public Integer duration;
   public String description;
   public String profileId;

   public static VsanStorageTestSpec fromVmodl(VsanStorageWorkloadType model) {
      VsanStorageTestSpec type = new VsanStorageTestSpec();
      type.typeId = model.typeId;
      type.name = model.name;
      type.description = model.description;
      return type;
   }
}
