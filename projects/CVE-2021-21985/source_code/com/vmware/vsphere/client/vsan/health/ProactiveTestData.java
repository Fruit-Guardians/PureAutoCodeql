package com.vmware.vsphere.client.vsan.health;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;

@data
public class ProactiveTestData extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public VsanTestData generalData;
   public Long timestamp;
   public ProactiveTestData.PerfTestType perfTestType;
   public String helpId;
   public ManagedObjectReference taskMoRef = null;

   @data
   public static enum PerfTestType {
      vmCreation,
      multicast,
      unicast;
   }
}
