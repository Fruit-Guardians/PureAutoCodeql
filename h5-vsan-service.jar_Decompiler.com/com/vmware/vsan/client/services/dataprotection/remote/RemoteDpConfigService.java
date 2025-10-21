package com.vmware.vsan.client.services.dataprotection.remote;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.vsan.DataProtectionInfo;
import com.vmware.vim.vsan.binding.vim.vsan.DataProtectionLoadBalancersInfo;
import com.vmware.vim.vsan.binding.vim.vsan.DataProtectionPairingInfo;
import com.vmware.vim.vsan.binding.vim.vsan.ReconfigSpec;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.dataprotection.model.PscConnectionDetails;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VsanClient;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RemoteDpConfigService {
   private static final Log logger = LogFactory.getLog(RemoteDpConfigService.class);
   @Autowired
   private VsanClient vsanClient;

   public DataProtectionInfo getDpConfig(ManagedObjectReference param1, PscConnectionDetails param2) throws VsanUiLocalizableException {
      // $FF: Couldn't be decompiled
   }

   public DataProtectionPairingInfo getPairingInfo(DataProtectionInfo dpConfig) {
      return dpConfig != null && !ArrayUtils.isEmpty(dpConfig.getPairingInfo()) ? dpConfig.getPairingInfo()[0] : null;
   }

   public ManagedObjectReference reconfigureCluster(ManagedObjectReference param1, PscConnectionDetails param2, ReconfigSpec param3) throws VsanUiLocalizableException {
      // $FF: Couldn't be decompiled
   }

   public DataProtectionLoadBalancersInfo getLoadBalancersInfo(ManagedObjectReference param1, PscConnectionDetails param2) throws VsanUiLocalizableException {
      // $FF: Couldn't be decompiled
   }
}
