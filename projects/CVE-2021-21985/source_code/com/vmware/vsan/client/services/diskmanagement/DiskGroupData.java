package com.vmware.vsan.client.services.diskmanagement;

import com.vmware.vim.binding.vim.host.ScsiDisk;
import com.vmware.vim.binding.vim.vsan.host.DiskMapping;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.vsan.host.DiskMapInfoEx;
import com.vmware.vise.core.model.data;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@data
public class DiskGroupData {
   public String name;
   public boolean isMounted;
   public DiskData[] disks;
   public ManagedObjectReference ownerHostRef;
   public boolean isAllFlash;
   public boolean isLocked;

   public static DiskGroupData fromMapping(ManagedObjectReference hostRef, DiskMapInfoEx mapInfo, Map<String, DiskData> claimedDisks) {
      DiskMapping mapping = mapInfo.getMapping();
      DiskGroupData result = new DiskGroupData();
      result.ownerHostRef = hostRef;
      result.name = mapping.ssd.vsanDiskInfo.vsanUuid;
      result.isAllFlash = mapInfo.isAllFlash;
      result.isMounted = mapInfo.isMounted;
      result.isLocked = mapInfo.encryptionInfo != null && mapInfo.encryptionInfo.encryptionEnabled && Boolean.FALSE.equals(mapInfo.unlockedEncrypted);
      List<DiskData> children = new ArrayList();
      String ssdUuid = mapping.ssd.uuid;
      if (claimedDisks.containsKey(ssdUuid)) {
         DiskData cacheDisk = (DiskData)claimedDisks.get(ssdUuid);
         cacheDisk.diskGroup = result.name;
         cacheDisk.isMappedAsCache = true;
         children.add(cacheDisk);
      }

      ScsiDisk[] var10;
      int var9 = (var10 = mapping.getNonSsd()).length;

      for(int var8 = 0; var8 < var9; ++var8) {
         ScsiDisk nonSsd = var10[var8];
         if (claimedDisks.containsKey(nonSsd.uuid)) {
            DiskData capacityDisk = (DiskData)claimedDisks.get(nonSsd.uuid);
            capacityDisk.isMappedAsCache = false;
            capacityDisk.diskGroup = result.name;
            children.add(capacityDisk);
         }
      }

      result.disks = (DiskData[])children.toArray(new DiskData[children.size()]);
      return result;
   }
}
