package com.vmware.vsphere.client.vsan.upgrade;

import com.vmware.vise.core.model.data;
import java.util.HashMap;
import java.util.Map;

@data
public class VsanVersionInfoPerHost {
   public Map<String, Integer> versions = new HashMap();

   public VsanVersionInfoPerHost(VsanDiskVersionData[] vsanDiskVersionsData) {
      if (vsanDiskVersionsData != null && vsanDiskVersionsData.length != 0) {
         VsanDiskVersionData[] var5 = vsanDiskVersionsData;
         int var4 = vsanDiskVersionsData.length;

         for(int var3 = 0; var3 < var4; ++var3) {
            VsanDiskVersionData versionData = var5[var3];
            String key = String.valueOf(versionData.version);
            if (key != null) {
               if (this.versions.containsKey(key)) {
                  this.versions.put(key, (Integer)this.versions.get(key) + 1);
               } else {
                  this.versions.put(key, 1);
               }
            }
         }

      }
   }
}
