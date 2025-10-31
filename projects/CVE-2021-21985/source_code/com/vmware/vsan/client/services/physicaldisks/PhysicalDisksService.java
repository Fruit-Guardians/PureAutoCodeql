package com.vmware.vsan.client.services.physicaldisks;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vsan.client.services.virtualobjects.VirtualObjectsService;
import com.vmware.vsphere.client.vsan.data.HostPhysicalMappingData;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PhysicalDisksService {
   private static final Log logger = LogFactory.getLog(PhysicalDisksService.class);
   @Autowired
   private VsanDiskMappingsProvider diskMappingsProvider;
   @Autowired
   private VirtualObjectsService virtualObjectsService;

   @TsService
   public List<PhysicalDisksHostData> getPhysicalDisksData(ManagedObjectReference clusterRef) throws Exception {
      List<HostPhysicalMappingData> vsanHostsPhysicalDiskData = this.diskMappingsProvider.getVsanHostsPhysicalDiskData(clusterRef);
      Object diskItems = new ArrayList();

      try {
         diskItems = this.virtualObjectsService.listVirtualObjects(clusterRef);
      } catch (Exception var8) {
         logger.error("Unable to extract physical disks virtual objects data: " + var8);
      }

      List<PhysicalDisksHostData> result = new ArrayList(vsanHostsPhysicalDiskData.size());
      Iterator var6 = vsanHostsPhysicalDiskData.iterator();

      while(var6.hasNext()) {
         HostPhysicalMappingData hostDisksMappingData = (HostPhysicalMappingData)var6.next();
         PhysicalDisksHostData hostData = new PhysicalDisksHostData(hostDisksMappingData);
         hostData.setVirtualObjectsData((List)diskItems);
         result.add(hostData);
      }

      return result;
   }
}
