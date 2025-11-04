package com.vmware.vsphere.client.vsan.health;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vise.core.model.data;

@data
public class ComplianceCheckResultObj extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public boolean isNew;
   public boolean hasChanged;
   public long originalCapacity = 0L;
   public long finalCapacity = 0L;
   public long initCapacity = 0L;
   public long finalUsedCapacity = 0L;
   public long originalCacheCapacity = 0L;
   public long finalCacheCapacity = 0L;
   public long initCacheCapacity = 0L;
   public long finalUsedCacheCapacity = 0L;
   public String uuid;
   public String name;
   public String objectType;
   public ComplianceCheckResultObj[] childDevices;
}
