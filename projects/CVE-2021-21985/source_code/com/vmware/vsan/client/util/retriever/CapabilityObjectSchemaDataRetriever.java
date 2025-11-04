package com.vmware.vsan.client.util.retriever;

import com.vmware.vim.binding.pbm.capability.provider.CapabilityObjectSchema;
import com.vmware.vim.binding.pbm.profile.ProfileManager;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.PbmClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.pbm.PbmConnection;

public class CapabilityObjectSchemaDataRetriever extends AbstractAsyncDataRetriever<CapabilityObjectSchema[]> {
   private PbmClient pbmClient;

   public CapabilityObjectSchemaDataRetriever(ManagedObjectReference clusterRef, Measure measure, PbmClient pbmClient) {
      super(clusterRef, measure);
      this.pbmClient = pbmClient;
   }

   public void start() {
      try {
         Throwable var1 = null;
         Object var2 = null;

         try {
            PbmConnection pbmConn = this.pbmClient.getConnection(this.clusterRef.getServerGuid());

            try {
               this.future = this.measure.newFuture("ProfileManager.FetchCapabilitySchema");
               ProfileManager profileManager = pbmConn.getProfileManager();
               profileManager.fetchCapabilitySchema((String)null, (String[])null, this.future);
            } finally {
               if (pbmConn != null) {
                  pbmConn.close();
               }

            }
         } catch (Throwable var12) {
            if (var1 == null) {
               var1 = var12;
            } else if (var1 != var12) {
               var1.addSuppressed(var12);
            }

            throw var1;
         }
      } catch (Exception var13) {
         this.future.setException(var13);
      }

   }
}
