package com.vmware.vsphere.client.vsandp.workflowbacking.recovery.failover.model;

import com.vmware.vise.core.model.data;
import java.util.Date;

@data
public class RemoteReplicationPointData implements Comparable<RemoteReplicationPointData> {
   public String key;
   public Date timestamp;

   public RemoteReplicationPointData() {
   }

   public RemoteReplicationPointData(String keyArg, Date timestampArg) {
      this.key = keyArg;
      this.timestamp = timestampArg;
   }

   public int compareTo(RemoteReplicationPointData another) {
      return this.timestamp.compareTo(another.timestamp);
   }

   public String toString() {
      return String.format("%s [key = %1$s, timestamp = %2$tY-%2$tm-%2$td %2$tH:%2$tM:%2$tS]", this.getClass().getName(), this.key, this.timestamp);
   }
}
