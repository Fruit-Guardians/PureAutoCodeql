package com.vmware.vsan.client.services.virtualobjects;

import com.vmware.vim.binding.vim.vm.device.VirtualDevice;
import com.vmware.vim.binding.vim.vm.device.VirtualDisk;
import com.vmware.vim.binding.vim.vm.device.VirtualDevice.FileBackingInfo;
import com.vmware.vim.vsan.binding.vim.host.VsanObjectHealth;
import com.vmware.vim.vsan.binding.vim.host.VsanObjectOverallHealth;
import com.vmware.vsan.client.services.virtualobjects.data.VirtualObjectHealthModel;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang.ArrayUtils;

public class VirtualObjectsUtil {
   public static VirtualDisk findDisk(VirtualDevice[] virtualDevices, String diskId) {
      if (virtualDevices == null) {
         return null;
      } else {
         VirtualDevice[] var5 = virtualDevices;
         int var4 = virtualDevices.length;

         for(int var3 = 0; var3 < var4; ++var3) {
            VirtualDevice device = var5[var3];
            if (device instanceof VirtualDisk) {
               VirtualDisk disk = (VirtualDisk)device;
               FileBackingInfo backing = findBacking(disk, diskId);
               if (backing != null) {
                  return disk;
               }
            }
         }

         return null;
      }
   }

   private static FileBackingInfo findBacking(VirtualDisk disk, String backingId) {
      if (!(disk.backing instanceof FileBackingInfo)) {
         return null;
      } else {
         FileBackingInfo backing = (FileBackingInfo)disk.getBacking();
         return backingId.equals(backing.backingObjectId) ? backing : null;
      }
   }

   public static Map<String, VirtualObjectHealthModel> getVsanObjectsHealthMap(VsanObjectOverallHealth healthData) {
      Map<String, VirtualObjectHealthModel> healthDataMap = new HashMap();
      if (healthData != null && !ArrayUtils.isEmpty(healthData.objectHealthDetail)) {
         VsanObjectHealth[] var5;
         int var4 = (var5 = healthData.objectHealthDetail).length;

         for(int var3 = 0; var3 < var4; ++var3) {
            VsanObjectHealth vsanObjectHealth = var5[var3];
            if (!ArrayUtils.isEmpty(vsanObjectHealth.objUuids)) {
               String[] var9;
               int var8 = (var9 = vsanObjectHealth.objUuids).length;

               for(int var7 = 0; var7 < var8; ++var7) {
                  String uuid = var9[var7];
                  healthDataMap.put(uuid, new VirtualObjectHealthModel(vsanObjectHealth.health, vsanObjectHealth.dataProtectionHealth));
               }
            }
         }

         return healthDataMap;
      } else {
         return healthDataMap;
      }
   }
}
