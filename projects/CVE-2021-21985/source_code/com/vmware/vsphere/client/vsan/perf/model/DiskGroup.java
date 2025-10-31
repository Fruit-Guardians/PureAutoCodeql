package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vim.binding.vim.host.ScsiDisk;
import com.vmware.vim.binding.vim.vsan.host.DiskMapping;
import com.vmware.vise.core.model.data;
import java.util.ArrayList;
import java.util.List;

@data
public class DiskGroup {
   public String diskGroupUuid;
   public String diskGroupName;
   public DiskInfo cacheDisk;
   public List<DiskInfo> capacityDisks;

   public static DiskGroup fromDiskMapping(DiskMapping diskMapping) {
      DiskGroup group = new DiskGroup();
      group.diskGroupName = diskMapping.ssd.vsanDiskInfo.vsanUuid;
      group.diskGroupUuid = diskMapping.ssd.vsanDiskInfo.vsanUuid;
      group.cacheDisk = new DiskInfo();
      group.cacheDisk.diskUuid = diskMapping.ssd.vsanDiskInfo.vsanUuid;
      group.cacheDisk.diskName = diskMapping.ssd.displayName;
      List<DiskInfo> capacityDisks = new ArrayList();
      ScsiDisk[] var6;
      int var5 = (var6 = diskMapping.nonSsd).length;

      for(int var4 = 0; var4 < var5; ++var4) {
         ScsiDisk disk = var6[var4];
         DiskInfo capacityDisk = new DiskInfo();
         capacityDisk.diskName = disk.displayName;
         capacityDisk.diskUuid = disk.vsanDiskInfo.vsanUuid;
         capacityDisks.add(capacityDisk);
      }

      group.capacityDisks = capacityDisks;
      return group;
   }
}
