package com.vmware.vsan.client.services.dataprotection.archive;

import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ReplicaSeriesManager.ClusterInfo;
import com.vmware.vise.core.model.data;

@data
public class ArchivalClusterData {
   public String uuid;
   public String name;

   public ArchivalClusterData() {
   }

   public ArchivalClusterData(String uuid, String name) {
      this.uuid = uuid;
      this.name = name;
   }

   public static ArchivalClusterData fromVmodl(ClusterInfo clusterInfo) {
      return new ArchivalClusterData(clusterInfo.key, clusterInfo.name);
   }

   public String toString() {
      return "ClusterData uuid: " + this.uuid + ", name: " + this.name;
   }
}
