package com.vmware.vsan.client.services.common.data;

import com.vmware.vim.binding.vim.vm.device.VirtualDevice;
import com.vmware.vim.binding.vim.vm.device.VirtualDisk;
import com.vmware.vim.binding.vim.vm.device.VirtualDevice.FileBackingInfo;
import com.vmware.vim.binding.vim.vm.device.VirtualDisk.FlatVer2BackingInfo;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;
import com.vmware.vise.data.query.PropertyValue;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

@data
public class VmData extends BasicVmData {
   public Map<String, VirtualDisk> uuidToVirtualDiskMap;
   public Map<String, FlatVer2BackingInfo> uuidToDiskSnapshotMap;
   public Map<FlatVer2BackingInfo, VirtualDisk> backingInfoToDiskMap;
   public String vmPathUuid;
   public Object namespaceCapabilityMetadata;

   public VmData(ManagedObjectReference vmRef) {
      super(vmRef);
   }

   public void updateVmData(PropertyValue propValue) {
      if (propValue != null && propValue.value != null) {
         String var2;
         switch((var2 = propValue.propertyName).hashCode()) {
         case -1099694814:
            if (var2.equals("namespaceCapabilityMetadata")) {
               this.namespaceCapabilityMetadata = propValue.value;
            }
            break;
         case -826278890:
            if (var2.equals("primaryIconId")) {
               this.primaryIconId = (String)propValue.value;
            }
            break;
         case -637434256:
            if (var2.equals("config.hardware.device")) {
               this.setVirtualDiskMaps((VirtualDevice[])propValue.value);
            }
            break;
         case 3373707:
            if (var2.equals("name")) {
               this.name = (String)propValue.value;
            }
            break;
         case 814403083:
            if (var2.equals("summary.config.vmPathName")) {
               this.vmPathUuid = this.getVmHomeVsanUuid((String)propValue.value);
            }
         }

      }
   }

   private String getVmHomeVsanUuid(String vmFilePath) {
      if (vmFilePath == null) {
         return null;
      } else {
         int startIndex = vmFilePath.indexOf(93);
         int endIndex = vmFilePath.indexOf(47);
         return startIndex >= 0 && endIndex > startIndex ? vmFilePath.substring(startIndex + 1, endIndex).trim() : null;
      }
   }

   public void setVirtualDiskMaps(VirtualDevice[] virtualDevices) {
      if (virtualDevices != null && virtualDevices.length != 0) {
         Map<String, VirtualDisk> uuidToVirtualDiskMap = new HashMap();
         Map<String, FlatVer2BackingInfo> uuidToDiskSnapshotMap = new HashMap();
         Map<FlatVer2BackingInfo, VirtualDisk> backingInfoToDiskMap = new HashMap();
         VirtualDevice[] var8 = virtualDevices;
         int var7 = virtualDevices.length;

         for(int var6 = 0; var6 < var7; ++var6) {
            VirtualDevice device = var8[var6];
            if (device instanceof VirtualDisk) {
               VirtualDisk disk = (VirtualDisk)device;
               if (disk.backing != null && disk.backing instanceof FileBackingInfo) {
                  FileBackingInfo fileBackingInfo = (FileBackingInfo)disk.backing;
                  if (StringUtils.isEmpty(fileBackingInfo.backingObjectId)) {
                     continue;
                  }

                  uuidToVirtualDiskMap.put(fileBackingInfo.backingObjectId, disk);
               }

               Object parentBackingInfoObject = getParentVirtualDiskBacking(disk);
               if (parentBackingInfoObject instanceof FlatVer2BackingInfo) {
                  for(FlatVer2BackingInfo parentBackingInfo = (FlatVer2BackingInfo)parentBackingInfoObject; parentBackingInfo != null; parentBackingInfo = parentBackingInfo.parent) {
                     uuidToDiskSnapshotMap.put(parentBackingInfo.backingObjectId, parentBackingInfo);
                     backingInfoToDiskMap.put(parentBackingInfo, disk);
                  }
               }
            }
         }

         this.uuidToDiskSnapshotMap = uuidToDiskSnapshotMap;
         this.backingInfoToDiskMap = backingInfoToDiskMap;
         this.uuidToVirtualDiskMap = uuidToVirtualDiskMap;
      }
   }

   private static Object getParentVirtualDiskBacking(VirtualDisk disk) {
      return disk.backing != null && disk.backing instanceof FlatVer2BackingInfo ? ((FlatVer2BackingInfo)disk.backing).parent : null;
   }

   public String getSnapshotName(String uuid) {
      FlatVer2BackingInfo parentBackingInfo = (FlatVer2BackingInfo)this.uuidToDiskSnapshotMap.get(uuid);
      VirtualDisk disk = (VirtualDisk)this.backingInfoToDiskMap.get(parentBackingInfo);
      return disk == null ? "" : String.format("%s - %s", disk.deviceInfo.label, getVirtualDiskFileName(parentBackingInfo.fileName));
   }

   private static String getVirtualDiskFileName(String filePath) {
      if (!StringUtils.isEmpty(filePath)) {
         String[] splittedPath = filePath.split("/");
         if (!ArrayUtils.isEmpty(splittedPath)) {
            return StringUtils.trim(splittedPath[splittedPath.length - 1]);
         }
      }

      return "";
   }
}
