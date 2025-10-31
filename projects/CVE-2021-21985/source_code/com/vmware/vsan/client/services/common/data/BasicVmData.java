package com.vmware.vsan.client.services.common.data;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;
import java.util.Comparator;

@data
public class BasicVmData {
   public String name;
   public String primaryIconId;
   public ManagedObjectReference vmRef;
   public static final Comparator<BasicVmData> COMPARATOR = new Comparator<BasicVmData>() {
      public int compare(BasicVmData o1, BasicVmData o2) {
         return o1.name.compareTo(o2.name);
      }
   };

   public BasicVmData(ManagedObjectReference vmRef) {
      this.vmRef = vmRef;
   }
}
