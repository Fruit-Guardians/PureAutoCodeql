package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vim.binding.pbm.profile.Profile;
import com.vmware.vim.binding.vim.KeyValue;
import com.vmware.vim.vsan.binding.vim.cluster.StorageComplianceResult;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectInformation;
import com.vmware.vise.core.model.data;

@data
public class PerfStatsObjectInfo {
   public boolean serviceEnabled;
   public String directoryName;
   public String vsanObjectUuid;
   public String vsanHealth;
   public KeyValue[] policyAttributes;
   public String spbmProfileUuid;
   public Profile spbmProfile;
   public StorageComplianceResult spbmComplianceResult;
   public String spbmProfileGenerationId;
   public boolean verboseModeEnabled;
   public boolean networkDiagnosticModeEnabled;

   public static PerfStatsObjectInfo fromVmodl(VsanObjectInformation vmodl) {
      PerfStatsObjectInfo info = null;
      if (vmodl != null) {
         info = new PerfStatsObjectInfo();
         info.directoryName = vmodl.directoryName;
         info.policyAttributes = vmodl.policyAttributes;
         info.spbmProfileGenerationId = vmodl.spbmProfileGenerationId;
         info.spbmProfileUuid = vmodl.spbmProfileUuid;
         info.vsanHealth = vmodl.vsanHealth;
         info.vsanObjectUuid = vmodl.vsanObjectUuid;
         info.spbmComplianceResult = vmodl.spbmComplianceResult;
      }

      return info;
   }
}
