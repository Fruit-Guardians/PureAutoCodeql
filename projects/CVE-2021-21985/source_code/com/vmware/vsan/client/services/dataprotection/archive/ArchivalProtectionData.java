package com.vmware.vsan.client.services.dataprotection.archive;

import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ReplicaSeriesManager.SeriesBasicInfo;
import com.vmware.vise.core.model.data;

@data
public class ArchivalProtectionData {
   public String seriesId;
   public String clusterUuid;
   public String clusterName;
   public String vmName;
   public String state;
   public String createdDate;
   public String decommissionDate;

   public static ArchivalProtectionData fromVmodl(SeriesBasicInfo seriesInfo) {
      ArchivalProtectionData protectionData = new ArchivalProtectionData();
      protectionData.seriesId = seriesInfo.key;
      protectionData.clusterUuid = seriesInfo.clusterOwner;
      protectionData.clusterName = seriesInfo.clusterOwnerName;
      protectionData.vmName = seriesInfo.displayName;
      protectionData.state = seriesInfo.state;
      protectionData.createdDate = seriesInfo.creationTimestamp;
      protectionData.decommissionDate = seriesInfo.decommissionTimestamp;
      return protectionData;
   }
}
