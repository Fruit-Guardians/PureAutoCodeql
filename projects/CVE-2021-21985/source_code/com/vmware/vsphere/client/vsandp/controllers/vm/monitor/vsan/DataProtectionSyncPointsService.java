package com.vmware.vsphere.client.vsandp.controllers.vm.monitor.vsan;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsandp.binding.vim.vsandp.CgInfo;
import com.vmware.vsphere.client.vsandp.controllers.vm.monitor.vsan.model.DpSyncPointsModel;
import com.vmware.vsphere.client.vsandp.controllers.vm.monitor.vsan.model.filter.DataProtectionInstanceFilter;
import com.vmware.vsphere.client.vsandp.controllers.vm.monitor.vsan.model.filter.DataProtectionInstanceFilterEnum;
import com.vmware.vsphere.client.vsandp.controllers.vm.monitor.vsan.model.filter.DataProtectionInstanceFilterSpec;
import com.vmware.vsphere.client.vsandp.controllers.vm.monitor.vsan.provider.pits.PitProvider;
import com.vmware.vsphere.client.vsandp.data.DataProtectionInstance;
import com.vmware.vsphere.client.vsandp.data.ProtectionType;
import com.vmware.vsphere.client.vsandp.dataproviders.vm.VmConsistencyGroupPropertyProvider;
import com.vmware.vsphere.client.vsandp.helper.VsanDpInventoryHelper;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DataProtectionSyncPointsService {
   private static final Logger logger = LoggerFactory.getLogger(DataProtectionSyncPointsService.class);
   @Autowired
   private VmConsistencyGroupPropertyProvider cgProvider;
   @Autowired
   private PitProvider pitProvider;
   @Autowired
   private VsanDpInventoryHelper inventoryHelper;
   // $FF: synthetic field
   private static int[] $SWITCH_TABLE$com$vmware$vsphere$client$vsandp$controllers$vm$monitor$vsan$model$filter$DataProtectionInstanceFilterEnum;

   @TsService
   public DpSyncPointsModel getVmSyncPoints(ManagedObjectReference vmRef, DataProtectionInstanceFilterSpec filter) throws Exception {
      logger.debug("Getting list of sync points for vm {}", vmRef);
      ManagedObjectReference clusterRef = this.inventoryHelper.getVmCluster(vmRef);
      CgInfo cgInfo = this.cgProvider.getCgInfo(vmRef, clusterRef);
      DpSyncPointsModel dpModel = this.buildSyncPointModel(vmRef, cgInfo, filter);
      dpModel.hasRestorePermission = this.inventoryHelper.isVmRestoreAllowed(vmRef);
      return dpModel;
   }

   @TsService
   public DpSyncPointsModel getIncomingReplicationSyncPoints(ManagedObjectReference clusterRef, String cgKey, DataProtectionInstanceFilterSpec filter) {
      CgInfo cgInfo = this.cgProvider.getCgInfo(clusterRef, cgKey);
      DpSyncPointsModel result = this.buildSyncPointModel((ManagedObjectReference)null, cgInfo, filter);
      boolean isTargetProtected = result.protectionTypes.contains(ProtectionType.TARGET);
      if (!isTargetProtected) {
         return result;
      } else {
         boolean hasPrivileges = false;

         try {
            hasPrivileges = this.inventoryHelper.hasRemoteDpActionsPrivileges(clusterRef);
         } catch (Exception var9) {
            logger.error("Error while retrieve privileges remote protection privileges.", var9);
         }

         if (!hasPrivileges) {
            return result;
         } else {
            cgInfo.getTarget();
            result.isTestAvailable = true;
            result.isCleanupAvailable = true;
            return result;
         }
      }
   }

   private DpSyncPointsModel buildSyncPointModel(ManagedObjectReference vmRef, CgInfo cgInfo, DataProtectionInstanceFilterSpec filter) {
      if (cgInfo == null) {
         logger.error("No protection data found for VM {}", vmRef);
         return null;
      } else {
         TreeSet<DataProtectionInstance> localPits = null;
         TreeSet<DataProtectionInstance> archivePits = null;
         TreeSet<DataProtectionInstance> remotePits = null;
         if (vmRef != null) {
            localPits = this.pitProvider.getLocalPits(vmRef, cgInfo);
            archivePits = this.pitProvider.getArchivePits(vmRef, cgInfo);
            remotePits = this.pitProvider.getRemotePits(vmRef, cgInfo);
         }

         TreeSet<DataProtectionInstance> targetPits = this.pitProvider.getTargetPits(cgInfo);
         return this.prepareSyncPointsModel(localPits, archivePits, remotePits, targetPits, filter);
      }
   }

   private DpSyncPointsModel prepareSyncPointsModel(TreeSet<DataProtectionInstance> localPits, TreeSet<DataProtectionInstance> archivePits, TreeSet<DataProtectionInstance> remotePits, TreeSet<DataProtectionInstance> targetPits, DataProtectionInstanceFilterSpec filter) {
      DpSyncPointsModel result = new DpSyncPointsModel();
      TreeSet<DataProtectionInstance> allInstances = new TreeSet();
      TreeSet<DataProtectionInstance> filteredInstances = new TreeSet(new DataProtectionSyncPointsService.DataProtectionInstanceComparator());
      if (CollectionUtils.isNotEmpty(localPits)) {
         allInstances.addAll(localPits);
         result.totalLocalSnapshotsSize = this.getTotalSize(localPits);
         localPits = this.filterPits(localPits, filter);
         result.instances.put(ProtectionType.LOCAL, localPits);
         filteredInstances.addAll(localPits);
         result.protectionTypes.add(ProtectionType.LOCAL);
      }

      if (CollectionUtils.isNotEmpty(archivePits)) {
         allInstances.addAll(archivePits);
         result.totalArchiveSnapshotsSize = this.getTotalSize(archivePits);
         archivePits = this.filterPits(archivePits, filter);
         result.instances.put(ProtectionType.ARCHIVE, archivePits);
         filteredInstances.addAll(archivePits);
         result.protectionTypes.add(ProtectionType.ARCHIVE);
      }

      if (CollectionUtils.isNotEmpty(remotePits)) {
         allInstances.addAll(remotePits);
         result.totalRemoteSnapshotsSize = this.getTotalSize(remotePits);
         remotePits = this.filterPits(remotePits, filter);
         result.instances.put(ProtectionType.REMOTE, remotePits);
         filteredInstances.addAll(remotePits);
         result.protectionTypes.add(ProtectionType.REMOTE);
      }

      if (CollectionUtils.isNotEmpty(targetPits)) {
         allInstances.addAll(targetPits);
         targetPits = this.filterPits(targetPits, filter);
         result.instances.put(ProtectionType.TARGET, targetPits);
         filteredInstances.addAll(targetPits);
         result.protectionTypes.add(ProtectionType.TARGET);
      }

      result.instancesCount = (long)allInstances.size();
      result.filterModel = this.getFilterModel(allInstances, filter.timezone);
      Iterator var10 = filteredInstances.iterator();

      while(var10.hasNext()) {
         DataProtectionInstance instance = (DataProtectionInstance)var10.next();
         result.headerInstances.add(instance.syncPoint);
      }

      if (!filteredInstances.isEmpty()) {
         result.lowestDate = ((DataProtectionInstance)filteredInstances.last()).syncPoint;
         result.highestDate = ((DataProtectionInstance)filteredInstances.first()).syncPoint;
      }

      this.appendEmptyHeaderPits(result);
      return result;
   }

   private long getTotalSize(Collection<DataProtectionInstance> pits) {
      long result = 0L;

      for(Iterator iter = pits.iterator(); iter.hasNext(); result += ((DataProtectionInstance)iter.next()).snapshotSize) {
      }

      return result;
   }

   private void appendEmptyHeaderPits(DpSyncPointsModel instanceModel) {
      Iterator var3 = instanceModel.headerInstances.iterator();

      while(var3.hasNext()) {
         Date date = (Date)var3.next();
         Iterator var5 = instanceModel.protectionTypes.iterator();

         while(var5.hasNext()) {
            ProtectionType protectionType = (ProtectionType)var5.next();
            TreeSet<DataProtectionInstance> instances = (TreeSet)instanceModel.instances.get(protectionType);
            DataProtectionInstance emptyInstance = DataProtectionInstance.getEmptyInstance(date);
            if (!instances.contains(emptyInstance)) {
               instances.add(emptyInstance);
            }
         }
      }

   }

   private TreeSet<DataProtectionInstance> filterPits(TreeSet<DataProtectionInstance> instances, DataProtectionInstanceFilterSpec filter) {
      TreeSet<DataProtectionInstance> result = new TreeSet(new DataProtectionSyncPointsService.DataProtectionInstanceComparator());
      Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(filter.timezone));
      calendar.set(11, 23);
      calendar.set(12, 59);
      calendar.set(13, 59);
      calendar.set(14, 999);
      Calendar calendarFrom = this.resetDate(Calendar.getInstance(TimeZone.getTimeZone(filter.timezone)));
      DataProtectionInstance instance;
      Iterator var7;
      switch($SWITCH_TABLE$com$vmware$vsphere$client$vsandp$controllers$vm$monitor$vsan$model$filter$DataProtectionInstanceFilterEnum()[filter.type.ordinal()]) {
      case 1:
         calendarFrom.add(5, -2);
         var7 = instances.iterator();

         while(true) {
            do {
               if (!var7.hasNext()) {
                  return result;
               }

               instance = (DataProtectionInstance)var7.next();
            } while(!instance.syncPoint.after(calendarFrom.getTime()) && !instance.syncPoint.equals(calendarFrom.getTime()));

            result.add(instance);
         }
      case 2:
         calendar.add(5, -3);
         calendarFrom.add(5, -6);
         var7 = instances.iterator();

         while(true) {
            do {
               do {
                  if (!var7.hasNext()) {
                     return result;
                  }

                  instance = (DataProtectionInstance)var7.next();
               } while(!instance.syncPoint.after(calendarFrom.getTime()) && !instance.syncPoint.equals(calendarFrom.getTime()));
            } while(!instance.syncPoint.before(calendar.getTime()) && !instance.syncPoint.equals(calendar.getTime()));

            result.add(instance);
         }
      case 3:
         calendar.add(5, -7);
         calendarFrom.add(5, -13);
         var7 = instances.iterator();

         while(true) {
            do {
               do {
                  if (!var7.hasNext()) {
                     return result;
                  }

                  instance = (DataProtectionInstance)var7.next();
               } while(!instance.syncPoint.after(calendarFrom.getTime()) && !instance.syncPoint.equals(calendarFrom.getTime()));
            } while(!instance.syncPoint.before(calendar.getTime()) && !instance.syncPoint.equals(calendar.getTime()));

            result.add(instance);
         }
      case 4:
         calendar.add(5, -14);
         calendarFrom.add(5, -27);
         var7 = instances.iterator();

         while(true) {
            do {
               do {
                  if (!var7.hasNext()) {
                     return result;
                  }

                  instance = (DataProtectionInstance)var7.next();
               } while(!instance.syncPoint.after(calendarFrom.getTime()) && !instance.syncPoint.equals(calendarFrom.getTime()));
            } while(!instance.syncPoint.before(calendar.getTime()) && !instance.syncPoint.equals(calendar.getTime()));

            result.add(instance);
         }
      case 5:
         calendar.add(5, -28);
         var7 = instances.iterator();

         while(var7.hasNext()) {
            instance = (DataProtectionInstance)var7.next();
            if (instance.syncPoint.before(calendar.getTime())) {
               result.add(instance);
            }
         }
      }

      return result;
   }

   private DataProtectionInstanceFilter getFilterModel(Set<DataProtectionInstance> allInstances, String timezone) {
      DataProtectionInstanceFilter result = new DataProtectionInstanceFilter();
      Calendar threeDaysCalendar = this.resetDate(Calendar.getInstance(TimeZone.getTimeZone(timezone)));
      threeDaysCalendar.add(5, -2);
      Calendar sevenDaysCalendar = this.resetDate(Calendar.getInstance(TimeZone.getTimeZone(timezone)));
      sevenDaysCalendar.add(5, -6);
      Calendar twoWeeksCalendar = this.resetDate(Calendar.getInstance(TimeZone.getTimeZone(timezone)));
      twoWeeksCalendar.add(5, -13);
      Calendar fourWeeksCalendar = this.resetDate(Calendar.getInstance(TimeZone.getTimeZone(timezone)));
      fourWeeksCalendar.add(5, -27);
      int newerThanThreeDays = 0;
      int betweenThreeAndSevenDays = 0;
      int betweenOneAndTwoWeeks = 0;
      int betweenTwoAndFourWeeks = 0;
      int olderThanFourWeeks = 0;
      Iterator var14 = allInstances.iterator();

      while(true) {
         while(true) {
            while(true) {
               while(true) {
                  while(var14.hasNext()) {
                     DataProtectionInstance instance = (DataProtectionInstance)var14.next();
                     if (!threeDaysCalendar.getTime().before(instance.syncPoint) && !threeDaysCalendar.getTime().equals(instance.syncPoint)) {
                        if (!sevenDaysCalendar.getTime().before(instance.syncPoint) && !sevenDaysCalendar.getTime().equals(instance.syncPoint)) {
                           if (!twoWeeksCalendar.getTime().before(instance.syncPoint) && !twoWeeksCalendar.getTime().equals(instance.syncPoint)) {
                              if (!fourWeeksCalendar.getTime().before(instance.syncPoint) && !fourWeeksCalendar.getTime().equals(instance.syncPoint)) {
                                 ++olderThanFourWeeks;
                              } else {
                                 ++betweenTwoAndFourWeeks;
                              }
                           } else {
                              ++betweenOneAndTwoWeeks;
                           }
                        } else {
                           ++betweenThreeAndSevenDays;
                        }
                     } else {
                        ++newerThanThreeDays;
                     }
                  }

                  result.newerThanThreeDays = (double)newerThanThreeDays / (double)allInstances.size() * 100.0D;
                  result.betweenThreeAndSevenDays = (double)betweenThreeAndSevenDays / (double)allInstances.size() * 100.0D;
                  result.betweenOneAndTwoWeeks = (double)betweenOneAndTwoWeeks / (double)allInstances.size() * 100.0D;
                  result.betweenTwoAndFourWeeks = (double)betweenTwoAndFourWeeks / (double)allInstances.size() * 100.0D;
                  result.olderThanFourWeeks = (double)olderThanFourWeeks / (double)allInstances.size() * 100.0D;
                  result.newerThanThreeDaysCount = newerThanThreeDays;
                  result.betweenThreeAndSevenDaysCount = betweenThreeAndSevenDays;
                  result.betweenOneAndTwoWeeksCount = betweenOneAndTwoWeeks;
                  result.betweenTwoAndFourWeeksCount = betweenTwoAndFourWeeks;
                  result.olderThanFourWeeksCount = olderThanFourWeeks;
                  return result;
               }
            }
         }
      }
   }

   private Calendar resetDate(Calendar calendar) {
      calendar.set(11, 0);
      calendar.set(12, 0);
      calendar.set(13, 0);
      calendar.set(14, 0);
      return calendar;
   }

   // $FF: synthetic method
   static int[] $SWITCH_TABLE$com$vmware$vsphere$client$vsandp$controllers$vm$monitor$vsan$model$filter$DataProtectionInstanceFilterEnum() {
      int[] var10000 = $SWITCH_TABLE$com$vmware$vsphere$client$vsandp$controllers$vm$monitor$vsan$model$filter$DataProtectionInstanceFilterEnum;
      if (var10000 != null) {
         return var10000;
      } else {
         int[] var0 = new int[DataProtectionInstanceFilterEnum.values().length];

         try {
            var0[DataProtectionInstanceFilterEnum.NEWER_THAN_THREE_DAYS.ordinal()] = 1;
         } catch (NoSuchFieldError var5) {
         }

         try {
            var0[DataProtectionInstanceFilterEnum.OLDER_THAN_FOUR_WEEKS.ordinal()] = 5;
         } catch (NoSuchFieldError var4) {
         }

         try {
            var0[DataProtectionInstanceFilterEnum.ONE_TWO_WEEKS.ordinal()] = 3;
         } catch (NoSuchFieldError var3) {
         }

         try {
            var0[DataProtectionInstanceFilterEnum.THREE_SEVEN_DAYS.ordinal()] = 2;
         } catch (NoSuchFieldError var2) {
         }

         try {
            var0[DataProtectionInstanceFilterEnum.TWO_FOUR_WEEKS.ordinal()] = 4;
         } catch (NoSuchFieldError var1) {
         }

         $SWITCH_TABLE$com$vmware$vsphere$client$vsandp$controllers$vm$monitor$vsan$model$filter$DataProtectionInstanceFilterEnum = var0;
         return var0;
      }
   }

   public static class DataProtectionInstanceComparator implements Comparator<DataProtectionInstance> {
      public int compare(DataProtectionInstance o1, DataProtectionInstance o2) {
         return o2.syncPoint.compareTo(o1.syncPoint);
      }
   }
}
