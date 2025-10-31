package com.vmware.vsan.client.util.retriever;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectIdentity;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectIdentityAndHealth;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectSystem;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;

class ObjectIdentitiesDataRetriever extends AbstractAsyncDataRetriever<VsanObjectIdentityAndHealth> {
   private Set<String> uuids;

   public ObjectIdentitiesDataRetriever(ManagedObjectReference clusterRef, Measure measure, Set<String> uuids) {
      super(clusterRef, measure);
      this.uuids = uuids;
   }

   public void start() {
      this.future = this.measure.newFuture("VsanObjectSystem.QueryObjectIdentities");
      VsanObjectSystem objectSystem = VsanProviderUtils.getVsanObjectSystem(this.clusterRef);
      String[] uuidsParam = CollectionUtils.isEmpty(this.uuids) ? null : (String[])this.uuids.toArray(new String[this.uuids.size()]);
      Boolean includeDpHealthData = VsanCapabilityUtils.isLocalDataProtectionSupported(this.clusterRef) ? true : null;
      objectSystem.queryObjectIdentities(this.clusterRef, uuidsParam, (String[])null, true, true, false, includeDpHealthData, this.future);
   }

   public VsanObjectIdentityAndHealth prepareResult() throws ExecutionException, InterruptedException {
      VsanObjectIdentityAndHealth result = (VsanObjectIdentityAndHealth)super.prepareResult();
      if (!ArrayUtils.isEmpty(result.identities)) {
         VsanObjectIdentity[] var5;
         int var4 = (var5 = result.identities).length;

         for(int var3 = 0; var3 < var4; ++var3) {
            VsanObjectIdentity id = var5[var3];
            if (id.vm != null) {
               id.vm.setServerGuid(this.clusterRef.getServerGuid());
            }
         }
      }

      return result;
   }
}
