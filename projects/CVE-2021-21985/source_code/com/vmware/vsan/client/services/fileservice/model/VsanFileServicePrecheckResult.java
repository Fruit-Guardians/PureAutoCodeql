package com.vmware.vsan.client.services.fileservice.model;

import com.vmware.vim.vsan.binding.vim.vsan.VsanFileServicePreflightCheckResult;
import com.vmware.vise.core.model.data;

@data
public class VsanFileServicePrecheckResult {
   public String directoryConnectivityIssue;
   public String hostVersion;
   public String mixedModeIssue;
   public String networkPartitionIssue;
   public String ovfInstalled;
   public String vsanDatastoreIssue;

   public static VsanFileServicePrecheckResult fromVmodl(VsanFileServicePreflightCheckResult vmodl) {
      if (vmodl == null) {
         return null;
      } else {
         VsanFileServicePrecheckResult result = new VsanFileServicePrecheckResult();
         result.directoryConnectivityIssue = vmodl.directoryConnectivityIssue;
         result.hostVersion = vmodl.hostVersion;
         result.mixedModeIssue = vmodl.mixedModeIssue;
         result.networkPartitionIssue = vmodl.networkPartitionIssue;
         result.ovfInstalled = vmodl.ovfInstalled;
         result.vsanDatastoreIssue = vmodl.vsanDatastoreIssue;
         return result;
      }
   }
}
