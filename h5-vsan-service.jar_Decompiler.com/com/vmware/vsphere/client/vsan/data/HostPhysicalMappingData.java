package com.vmware.vsphere.client.vsan.data;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;
import java.util.List;

@data
public class HostPhysicalMappingData extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public ManagedObjectReference hostRef;
   public ManagedObjectReference clusterRef;
   public String name;
   public String primaryIconId;
   public List<PhysicalDiskData> physicalDisks;
   public Object[] storageAdapterDevices;
   public String faultDomain;

   public HostPhysicalMappingData() {
   }

   public HostPhysicalMappingData(ManagedObjectReference clusterRef, ManagedObjectReference hostRef, String hostName, String primaryIconId, List<PhysicalDiskData> physicalDsks, Object[] storageAdapters, String faultDomain) {
      this.clusterRef = clusterRef;
      this.hostRef = hostRef;
      this.name = hostName;
      this.primaryIconId = primaryIconId;
      this.physicalDisks = physicalDsks;
      this.storageAdapterDevices = storageAdapters;
      this.faultDomain = faultDomain;
   }
}
