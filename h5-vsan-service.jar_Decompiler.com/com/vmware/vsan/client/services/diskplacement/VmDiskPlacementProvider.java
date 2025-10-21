package com.vmware.vsan.client.services.diskplacement;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.vm.ConfigInfo;
import com.vmware.vim.binding.vim.vm.device.VirtualDevice;
import com.vmware.vim.binding.vim.vm.device.VirtualDisk;
import com.vmware.vim.binding.vim.vm.device.VirtualDevice.FileBackingInfo;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vsan.client.services.virtualobjects.VirtualObjectsService;
import com.vmware.vsan.client.services.virtualobjects.data.VirtualObjectModel;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsphere.client.vsan.util.DataServiceResponse;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VmDiskPlacementProvider {
   private static final String SWAP_STORAGE_OBJECT_ID = "config.swapStorageObjectId";
   @Autowired
   private VirtualObjectsService virtualObjectsService;

   @TsService
   public List<VirtualObjectModel> getVmVirtualObjects(ManagedObjectReference vmRef) throws Exception {
      DataServiceResponse vmProperties = QueryUtil.getProperties(vmRef, new String[]{"cluster", "config.hardware.device", "config.vmStorageObjectId", "config.swapStorageObjectId"});
      ManagedObjectReference vmCluster = (ManagedObjectReference)vmProperties.getProperty(vmRef, "cluster");
      Set<String> vmObjectUuids = new HashSet();
      String vmHomeObjectUuid = (String)vmProperties.getProperty(vmRef, "config.vmStorageObjectId");
      vmObjectUuids.add(vmHomeObjectUuid);
      String vmSwapObjectUuid = (String)vmProperties.getProperty(vmRef, "config.swapStorageObjectId");
      if (StringUtils.isNotEmpty(vmSwapObjectUuid)) {
         vmObjectUuids.add(vmSwapObjectUuid);
      }

      VirtualDevice[] vmDevices = (VirtualDevice[])vmProperties.getProperty(vmRef, "config.hardware.device");
      vmObjectUuids.addAll(this.getVmDiskObjectUuids(vmDevices));
      Throwable var8 = null;
      Object var9 = null;

      try {
         Measure measure = new Measure("Collect VM's snapshots");

         try {
            Collection<ConfigInfo> configSnapshots = this.virtualObjectsService.listVmSnapshots(vmRef, measure);
            Iterator var13 = configSnapshots.iterator();

            while(var13.hasNext()) {
               ConfigInfo configSnapshot = (ConfigInfo)var13.next();
               vmObjectUuids.addAll(this.getVmDiskObjectUuids(configSnapshot.hardware.device));
            }
         } finally {
            if (measure != null) {
               measure.close();
            }

         }
      } catch (Throwable var19) {
         if (var8 == null) {
            var8 = var19;
         } else if (var8 != var19) {
            var8.addSuppressed(var19);
         }

         throw var8;
      }

      return this.virtualObjectsService.listVmVirtualObjects(vmCluster, vmRef, vmObjectUuids);
   }

   private Set<String> getVmDiskObjectUuids(VirtualDevice[] vmDevices) {
      Set<String> result = new HashSet();
      VirtualDevice[] var6 = vmDevices;
      int var5 = vmDevices.length;

      for(int var4 = 0; var4 < var5; ++var4) {
         VirtualDevice device = var6[var4];
         if (device instanceof VirtualDisk) {
            VirtualDisk disk = (VirtualDisk)device;
            if (disk.backing != null && disk.backing instanceof FileBackingInfo) {
               FileBackingInfo fileBackingInfo = (FileBackingInfo)disk.backing;
               if (!StringUtils.isEmpty(fileBackingInfo.backingObjectId)) {
                  result.add(fileBackingInfo.backingObjectId);
               }
            }
         }
      }

      return result;
   }
}
