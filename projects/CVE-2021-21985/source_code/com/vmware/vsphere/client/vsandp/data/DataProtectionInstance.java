package com.vmware.vsphere.client.vsandp.data;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsandp.binding.vim.vsandp.GroupInstanceData;
import com.vmware.vim.vsandp.binding.vim.vsandp.QuiescedType;
import com.vmware.vim.vsandp.binding.vim.vsandp.GroupInstanceData.ObjectSnapshot;
import com.vmware.vise.core.model.data;
import java.util.Calendar;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@data
public class DataProtectionInstance implements Comparable<DataProtectionInstance> {
   private static final Logger logger = LoggerFactory.getLogger(DataProtectionInstance.class);
   public ManagedObjectReference vmRef;
   public String cgKey;
   public String id;
   public String seriesKey;
   public ProtectionType type;
   public Date syncPoint;
   public long snapshotSize;
   public long snapshotDelay;
   public long transferDuration;
   public boolean hasRpoViolation;
   public DataProtectionInstance.QuiescingType quiescingType;
   public boolean hasQuiesceError;
   public boolean isInProgress;
   public boolean isManual;

   public DataProtectionInstance() {
   }

   public DataProtectionInstance(ManagedObjectReference vmRef) {
      this.vmRef = vmRef;
   }

   public DataProtectionInstance(ManagedObjectReference vmRef, String cgKey, String id, String seriesKey, ProtectionType type, Date syncPoint, long snapshotSize, long snapshotDelay, long transferDuration, boolean hasRpoViolation, DataProtectionInstance.QuiescingType quiescingType, boolean hasQuiesceError, boolean isInProgress, boolean isManual) {
      this.vmRef = vmRef;
      this.cgKey = cgKey;
      this.id = id;
      this.seriesKey = seriesKey;
      this.type = type;
      this.syncPoint = syncPoint;
      this.snapshotSize = snapshotSize;
      this.snapshotDelay = snapshotDelay;
      this.transferDuration = transferDuration;
      this.hasRpoViolation = hasRpoViolation;
      this.quiescingType = quiescingType;
      this.hasQuiesceError = hasQuiesceError;
      this.isInProgress = isInProgress;
      this.isManual = isManual;
   }

   public static DataProtectionInstance getEmptyInstance(Date date) {
      return new DataProtectionInstance((ManagedObjectReference)null, (String)null, (String)null, (String)null, ProtectionType.LOCAL, date, 0L, 0L, 0L, false, DataProtectionInstance.QuiescingType.NONE, false, false, false);
   }

   public static DataProtectionInstance createInstance(String seriesKey, ProtectionType type, GroupInstanceData instance, ManagedObjectReference vmRef, String cgKey) {
      long duration = 0L;
      Calendar startTime = instance.getSnapshotTimestamp();
      if (instance.getInstanceStats().getEndTime() != null) {
         duration = instance.getInstanceStats().getEndTime().getTimeInMillis() - startTime.getTimeInMillis();
      } else {
         duration = Calendar.getInstance().getTimeInMillis() - instance.getInstanceStats().getStartTime().getTimeInMillis();
      }

      boolean isManual = checkCgForManualSnapshots(instance);
      return new DataProtectionInstance(vmRef, cgKey, instance.getKey(), seriesKey, type, startTime.getTime(), instance.getAllocatedBytes(), (long)instance.getSnapshotLateness(), duration / 1000L, instance.snapshotLateness > 0, DataProtectionInstance.QuiescingType.fromQuescedType(instance.quiescedType), instance.quiesceExpected && QuiescedType.valueOf(instance.quiescedType) == QuiescedType.none, false, isManual);
   }

   private static boolean checkCgForManualSnapshots(GroupInstanceData instance) {
      if (instance.getMember() != null && instance.getMember().length != 0) {
         DataProtectionInstance.QuiescingType type = DataProtectionInstance.QuiescingType.fromQuescedType(instance.quiescedType);
         ObjectSnapshot[] var5;
         int var4 = (var5 = instance.getMember()).length;

         for(int var3 = 0; var3 < var4; ++var3) {
            ObjectSnapshot os = var5[var3];
            if (type.equals(DataProtectionInstance.QuiescingType.NONE)) {
               if (os.getNumManagedSnapshots() > 0) {
                  return true;
               }
            } else if (os.getNumManagedSnapshots() > 1) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public int compareTo(DataProtectionInstance o) {
      int result = this.syncPoint.compareTo(o.syncPoint);
      if (result == 0) {
         result = -this.type.compareTo(o.type);
      }

      return result;
   }

   @data
   public static enum QuiescingType {
      NONE,
      APP_CONSISTENCY,
      FILE_SYSTEM_CONSISTENCY;

      // $FF: synthetic field
      private static int[] $SWITCH_TABLE$com$vmware$vim$vsandp$binding$vim$vsandp$QuiescedType;

      public static DataProtectionInstance.QuiescingType fromQuescedType(String type) {
         switch($SWITCH_TABLE$com$vmware$vim$vsandp$binding$vim$vsandp$QuiescedType()[QuiescedType.valueOf(type).ordinal()]) {
         case 1:
            return NONE;
         case 2:
            return APP_CONSISTENCY;
         case 3:
            return FILE_SYSTEM_CONSISTENCY;
         default:
            DataProtectionInstance.logger.error("Received unsupported quiescing type from backend: {}", type);
            throw new IllegalArgumentException("Unknown quiescing type: " + type);
         }
      }

      // $FF: synthetic method
      static int[] $SWITCH_TABLE$com$vmware$vim$vsandp$binding$vim$vsandp$QuiescedType() {
         int[] var10000 = $SWITCH_TABLE$com$vmware$vim$vsandp$binding$vim$vsandp$QuiescedType;
         if (var10000 != null) {
            return var10000;
         } else {
            int[] var0 = new int[QuiescedType.values().length];

            try {
               var0[QuiescedType.applicationQuiesced.ordinal()] = 2;
            } catch (NoSuchFieldError var3) {
            }

            try {
               var0[QuiescedType.fileSystemQuiesced.ordinal()] = 3;
            } catch (NoSuchFieldError var2) {
            }

            try {
               var0[QuiescedType.none.ordinal()] = 1;
            } catch (NoSuchFieldError var1) {
            }

            $SWITCH_TABLE$com$vmware$vim$vsandp$binding$vim$vsandp$QuiescedType = var0;
            return var0;
         }
      }
   }
}
