package com.vmware.vsphere.client.vsan.health;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vise.core.model.data;

@data
public class ComplianceCheckSummary extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public int originalFaultDomainCount = 0;
   public int newFaultDomainCount = 0;
   public int originalHostCount = 0;
   public int newHostCount = 0;
   public int originalDiskGroupCount = 0;
   public int newDiskGroupCount = 0;
   public int originalSSDCount = 0;
   public int newSSDCount = 0;
   public int originalCapacityDeviceCount = 0;
   public int newCapacityDeviceCount = 0;
   public long originalTotalCapacity = 0L;
   public long newFinalTotalCapacity = 0L;
   public long originalUsedCapacity = 0L;
   public long newFinalUsedCapacity = 0L;
}
