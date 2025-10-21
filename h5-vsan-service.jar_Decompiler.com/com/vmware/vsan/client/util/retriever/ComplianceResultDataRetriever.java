package com.vmware.vsan.client.util.retriever;

import com.vmware.vim.binding.pbm.ServerObjectRef;
import com.vmware.vim.binding.pbm.ServerObjectRef.ObjectType;
import com.vmware.vim.binding.pbm.compliance.ComplianceManager;
import com.vmware.vim.binding.pbm.compliance.ComplianceResult;
import com.vmware.vim.binding.pbm.profile.ProfileId;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vsan.client.services.cns.model.Volume;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.PbmClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.pbm.PbmConnection;

public class ComplianceResultDataRetriever extends AbstractAsyncDataRetriever<ComplianceResult[]> {
   private final PbmClient pbmClient;
   private final Volume volume;

   public ComplianceResultDataRetriever(ManagedObjectReference clusterRef, Measure measure, PbmClient pbmClient, Volume volume) {
      super(clusterRef, measure);
      this.pbmClient = pbmClient;
      this.volume = volume;
   }

   public void start() {
      try {
         Throwable var1 = null;
         Object var2 = null;

         try {
            PbmConnection pbmConn = this.pbmClient.getConnection(this.clusterRef.getServerGuid());

            try {
               this.future = this.measure.newFuture("ComplianceManager.FetchComplianceResult");
               ComplianceManager complianceManager = pbmConn.getComplianceManager();
               ServerObjectRef[] serverObjectRefs = this.createServerObjectRefs(this.volume.id, this.clusterRef.getServerGuid());
               complianceManager.fetchComplianceResult(serverObjectRefs, (ProfileId)null, this.future);
            } finally {
               if (pbmConn != null) {
                  pbmConn.close();
               }

            }
         } catch (Throwable var13) {
            if (var1 == null) {
               var1 = var13;
            } else if (var1 != var13) {
               var1.addSuppressed(var13);
            }

            throw var1;
         }
      } catch (Exception var14) {
         this.future.setException(var14);
      }

   }

   private ServerObjectRef[] createServerObjectRefs(String volumeId, String serverGuild) {
      ServerObjectRef serverObjectRef = new ServerObjectRef();
      serverObjectRef.setObjectType(ObjectType.virtualDiskUUID.toString());
      serverObjectRef.setKey(volumeId);
      serverObjectRef.setServerUuid(serverGuild);
      return new ServerObjectRef[]{serverObjectRef};
   }
}
