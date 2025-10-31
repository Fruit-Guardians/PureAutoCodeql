package com.vmware.vsphere.client.vsandp.workflowbacking.recovery.failover.model;

import com.vmware.vise.core.model.data;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

@data
public class RemoteReplicationData {
   public String vmReplicaName;
   public SortedSet<RemoteReplicationPointData> points = new TreeSet(Collections.reverseOrder());
   public String seriesKey;
   public String remoteClusterUuid;

   public RemoteReplicationData() {
   }

   public RemoteReplicationData(String vmReplicaNameArg, String seriesKeyArg, String remoteClusterUuidArg) {
      this.vmReplicaName = vmReplicaNameArg;
      this.seriesKey = seriesKeyArg;
      this.remoteClusterUuid = remoteClusterUuidArg;
   }

   public String toString() {
      StringBuilder pointsSb = new StringBuilder();
      if (this.points.size() > 0) {
         pointsSb.append("[" + this.points.first() + "]");
      }

      if (this.points.size() > 1) {
         pointsSb.append(" ... [" + this.points.last() + "]");
      }

      return String.format("%s [vmReplicaName = %s, seriesKey = %s, remoteClusterUuid = %s, points (count=%d) = %s]", this.getClass().getName(), this.vmReplicaName, this.seriesKey, this.remoteClusterUuid, this.points.size(), pointsSb.toString());
   }
}
