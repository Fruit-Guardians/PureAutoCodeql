package com.vmware.vsphere.client.vsan.util;

import com.vmware.vim.binding.vim.host.ScsiDisk;
import com.vmware.vsphere.client.vsan.data.ClaimOption;
import com.vmware.vsphere.client.vsan.data.VsanDiskData;
import com.vmware.vsphere.client.vsan.data.VsanSemiAutoClaimDisksData;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;

public class VsanAllFlashClaimOptionRecommender extends VsanBaseClaimOptionRecommender {
   private static final int CACHE_TO_CAPACITY_DIVIDER = 8;
   private static final int CACHE_TO_CAPACITY_SIZE_DIVIDER = 11;
   private boolean _isAllFlashAvailable = true;
   private Map<String, ClaimOption> _HCL = null;

   public VsanAllFlashClaimOptionRecommender(VsanSemiAutoClaimDisksData data, Map<String, ClaimOption> HCL) {
      super(data);
      this._isAllFlashAvailable = data.isAllFlashAvailable;
      this._HCL = HCL;
   }

   public void recommend() {
      if (!ArrayUtils.isEmpty(this.getData().notInUseDisks)) {
         if (this._isAllFlashAvailable && (this._HCL == null || this._HCL.size() <= 0)) {
            this.makeAllFlashConfigRecommendation(this.getData());
         }

      }
   }

   private void makeAllFlashConfigRecommendation(VsanSemiAutoClaimDisksData data) {
      SortedMap<Long, List<VsanDiskData>> ssdsBySize = new TreeMap();
      List<VsanDiskData> storageSsdDisks = new ArrayList();
      long disksCapacity = 0L;
      int disksCount = 0;
      VsanDiskData[] var10;
      int var9 = (var10 = data.notInUseDisks).length;

      for(int var8 = 0; var8 < var9; ++var8) {
         VsanDiskData disk = var10[var8];
         ScsiDisk scsiDisk = disk.disk;
         if (scsiDisk.ssd) {
            ++disksCount;
            Long capacity = calculateSize(scsiDisk.capacity);
            disksCapacity += capacity;
            if (disk.markedAsCapacityFlash) {
               storageSsdDisks.add(disk);
            } else {
               if (!ssdsBySize.containsKey(capacity)) {
                  ssdsBySize.put(capacity, new ArrayList());
               }

               ((List)ssdsBySize.get(capacity)).add(disk);
            }
         }
      }

      if (ssdsBySize.size() == 1) {
         this.makeConfigRecommendation((List)null, (List)ssdsBySize.get(ssdsBySize.firstKey()), data.numAllFlashGroups);
      } else if (ssdsBySize.size() > 1) {
         long minCacheCapacity = disksCapacity / 11L;
         VsanAllFlashClaimOptionRecommender.SortedDiskGroups<VsanDiskData> ssdGroupsBySize = new VsanAllFlashClaimOptionRecommender.SortedDiskGroups((VsanAllFlashClaimOptionRecommender.SortedDiskGroups)null);
         Iterator var21 = ssdsBySize.entrySet().iterator();

         while(var21.hasNext()) {
            Entry<Long, List<VsanDiskData>> entry = (Entry)var21.next();
            ssdGroupsBySize.addDiskGroup((Long)entry.getKey() * (long)((List)entry.getValue()).size(), (List)entry.getValue());
         }

         boolean moreCacheNeeded = true;
         int cacheDisksCount = 0;
         List<VsanDiskData> cacheSsdDisks = new LinkedList();
         long currentCache = 0L;

         List disks;
         while(ssdGroupsBySize.getDiskGroupsCount() != 0) {
            Long size = ssdGroupsBySize.getSmallestDiskGroupCapacity();
            currentCache += size;
            if (currentCache >= minCacheCapacity) {
               moreCacheNeeded = false;
            }

            disks = ssdGroupsBySize.getSmallestGroupListWithLeastDisks();
            cacheSsdDisks.addAll(disks);
            cacheDisksCount += disks.size();
            if (!moreCacheNeeded) {
               break;
            }
         }

         int maxCacheDisks = (disksCount - 1) / 8 + 1;

         while(true) {
            if (maxCacheDisks <= cacheDisksCount || ssdGroupsBySize.getDiskGroupsCount() == 0) {
               storageSsdDisks.addAll(ssdGroupsBySize.getAllDisks());
               if (storageSsdDisks.size() == 0 || cacheSsdDisks.size() > storageSsdDisks.size()) {
                  return;
               }

               this.makeConfigRecommendation(cacheSsdDisks, storageSsdDisks, data.numAllFlashGroups);
               break;
            }

            disks = ssdGroupsBySize.getSmallestGroupListWithLeastDisks();
            cacheDisksCount += disks.size();
            cacheSsdDisks.addAll(disks);
         }
      }

   }

   protected void markDisksForClaimingOption(List<VsanDiskData> disks, ClaimOption option) {
      if (!CollectionUtils.isEmpty(disks)) {
         VsanDiskData disk;
         for(Iterator var4 = disks.iterator(); var4.hasNext(); disk.recommendedAllFlashClaimOption = option) {
            disk = (VsanDiskData)var4.next();
         }

      }
   }

   private static class DiskGroupsList<T> {
      private List<List<T>> _list;

      private DiskGroupsList() {
         this._list = new ArrayList();
      }

      public void addDiskGroup(List<T> group) {
         this._list.add(group);
      }

      public List<List<T>> getAllDiskGroups() {
         return this._list;
      }

      public List<T> getItemWithSmallestSize() {
         List<T> result = null;
         if (this._list.size() > 1) {
            int minimumDisks = ((List)this._list.get(0)).size();
            int indexMinimumSize = 0;

            for(int i = 0; i < this._list.size(); ++i) {
               if (minimumDisks > ((List)this._list.get(i)).size()) {
                  minimumDisks = ((List)this._list.get(i)).size();
                  indexMinimumSize = i;
               }
            }

            result = (List)this._list.remove(indexMinimumSize);
         } else if (this._list.size() == 1) {
            result = (List)this._list.get(0);
         }

         return result;
      }

      // $FF: synthetic method
      DiskGroupsList(VsanAllFlashClaimOptionRecommender.DiskGroupsList var1) {
         this();
      }
   }

   private static class SortedDiskGroups<T> {
      private SortedMap<Long, VsanAllFlashClaimOptionRecommender.DiskGroupsList<T>> _map;

      private SortedDiskGroups() {
         this._map = new TreeMap();
      }

      public void addDiskGroup(Long groupSize, List<T> group) {
         if (!this._map.containsKey(groupSize)) {
            this._map.put(groupSize, new VsanAllFlashClaimOptionRecommender.DiskGroupsList((VsanAllFlashClaimOptionRecommender.DiskGroupsList)null));
         }

         VsanAllFlashClaimOptionRecommender.DiskGroupsList<T> list = (VsanAllFlashClaimOptionRecommender.DiskGroupsList)this._map.get(groupSize);
         list.addDiskGroup(group);
      }

      public List<T> getSmallestGroupListWithLeastDisks() {
         return this.getGroupListWithLeastDisks(this.getSmallestDiskGroupCapacity());
      }

      private List<T> getGroupListWithLeastDisks(Long size) {
         List<T> result = null;
         VsanAllFlashClaimOptionRecommender.DiskGroupsList<T> currentItem = (VsanAllFlashClaimOptionRecommender.DiskGroupsList)this._map.get(size);
         if (currentItem.getAllDiskGroups().size() > 1) {
            result = currentItem.getItemWithSmallestSize();
         } else {
            result = ((VsanAllFlashClaimOptionRecommender.DiskGroupsList)this._map.remove(size)).getItemWithSmallestSize();
         }

         return result;
      }

      public int getDiskGroupsCount() {
         return this._map.size();
      }

      public List<T> getAllDisks() {
         List<T> result = new ArrayList();
         Iterator var3 = this._map.values().iterator();

         while(var3.hasNext()) {
            VsanAllFlashClaimOptionRecommender.DiskGroupsList<T> groups = (VsanAllFlashClaimOptionRecommender.DiskGroupsList)var3.next();
            Iterator var5 = groups.getAllDiskGroups().iterator();

            while(var5.hasNext()) {
               List<T> diskGroup = (List)var5.next();
               result.addAll(diskGroup);
            }
         }

         return result;
      }

      public Long getSmallestDiskGroupCapacity() {
         return (Long)this._map.firstKey();
      }

      // $FF: synthetic method
      SortedDiskGroups(VsanAllFlashClaimOptionRecommender.SortedDiskGroups var1) {
         this();
      }
   }
}
