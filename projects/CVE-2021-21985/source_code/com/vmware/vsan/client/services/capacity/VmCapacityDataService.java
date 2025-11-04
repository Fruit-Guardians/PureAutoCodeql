package com.vmware.vsan.client.services.capacity;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.vm.device.VirtualDevice;
import com.vmware.vim.binding.vim.vm.device.VirtualDisk;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanContainerSpaceUsage;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectSpaceSummary;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.capacity.model.VmCapacityData;
import com.vmware.vsphere.client.vsan.base.data.VsanObjectType;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VsanClient;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VmCapacityDataService {
   private static final Log logger = LogFactory.getLog(VmCapacityDataService.class);
   @Autowired
   VsanClient vsanClient;
   @Autowired
   CapacityDataService clusterCapacityService;

   @TsService
   public VmCapacityData getVmSpaceUsage(ManagedObjectReference param1) {
      // $FF: Couldn't be decompiled
   }

   private long getVmTotalDiskSize(ManagedObjectReference vmRef) throws VsanUiLocalizableException {
      try {
         long result = 0L;
         VirtualDevice[] devices = (VirtualDevice[])QueryUtil.getProperty(vmRef, "config.hardware.device");
         VirtualDevice[] var8 = devices;
         int var7 = devices.length;

         for(int var6 = 0; var6 < var7; ++var6) {
            VirtualDevice device = var8[var6];
            if (device instanceof VirtualDisk) {
               VirtualDisk disk = (VirtualDisk)device;
               result += disk.capacityInBytes;
            }
         }

         return result;
      } catch (Exception var10) {
         logger.error("Unable to get VM's disks to determine total disks capacity", var10);
         throw new VsanUiLocalizableException("vsan.common.generic.error");
      }
   }

   private Map<VsanObjectType, VsanObjectSpaceSummary> getSpaceUsageByObjectType(VsanContainerSpaceUsage vsanObjectSpaceUsage) {
      Map<VsanObjectType, VsanObjectSpaceSummary> result = new HashMap();
      VsanObjectSpaceSummary[] var6;
      int var5 = (var6 = vsanObjectSpaceUsage.spaceUsageByObjectType).length;

      for(int var4 = 0; var4 < var5; ++var4) {
         VsanObjectSpaceSummary spaceSummary = var6[var4];
         result.put(VsanObjectType.parse(spaceSummary.objType), spaceSummary);
      }

      return result;
   }
}
