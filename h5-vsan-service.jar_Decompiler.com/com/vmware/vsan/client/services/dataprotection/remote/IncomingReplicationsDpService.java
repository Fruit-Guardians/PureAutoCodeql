package com.vmware.vsan.client.services.dataprotection.remote;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService.TargetFilterSpec;
import com.vmware.vsan.client.services.dataprotection.ProtectionsMonitorService;
import com.vmware.vsan.client.services.dataprotection.model.ProtectionsMonitorData;
import com.vmware.vsan.client.util.VmodlHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IncomingReplicationsDpService extends ProtectionsMonitorService {
   private static final Log logger = LogFactory.getLog(IncomingReplicationsDpService.class);
   @Autowired
   private VmodlHelper vmodlHelper;

   protected TargetFilterSpec buildProtectionFilter() {
      TargetFilterSpec result = super.buildProtectionFilter();
      result.setTargetRequested(true);
      return result;
   }

   @TsService
   public ProtectionsMonitorData getIncomingReplicationsData(ManagedObjectReference clusterRef, String sourceDsUrl) {
      return this.getProtectionsData(clusterRef, sourceDsUrl);
   }
}
