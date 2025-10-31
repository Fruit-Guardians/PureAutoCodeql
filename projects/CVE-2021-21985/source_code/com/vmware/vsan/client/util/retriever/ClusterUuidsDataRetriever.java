package com.vmware.vsan.client.util.retriever;

import com.vmware.vim.binding.vim.host.VsanInternalSystem;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vmomi.core.Future;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.dataprovider.VsanHostPropertyProviderAdapter;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsan.util.Utils;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonNode;

class ClusterUuidsDataRetriever extends AbstractAsyncDataRetriever<Set<String>> {
   private static final Log logger = LogFactory.getLog(ClusterUuidsDataRetriever.class);
   private Map<ManagedObjectReference, Future<String>> hostTasks;
   private final VcClient vcClient;
   private final VmodlHelper vmodlHelper;

   public ClusterUuidsDataRetriever(ManagedObjectReference clusterRef, Measure measure, VcClient vcClient, VmodlHelper vmodlHelper) {
      super(clusterRef, measure);
      this.vcClient = vcClient;
      this.vmodlHelper = vmodlHelper;
   }

   public void start() {
      ManagedObjectReference[] hosts;
      Throwable var2;
      Object var3;
      try {
         var2 = null;
         var3 = null;

         try {
            Measure collectHosts = this.measure.start("hosts(" + this.clusterRef.getValue() + ")");

            try {
               hosts = (ManagedObjectReference[])QueryUtil.getProperty(this.clusterRef, "host", (Object)null);
            } finally {
               if (collectHosts != null) {
                  collectHosts.close();
               }

            }
         } catch (Throwable var33) {
            if (var2 == null) {
               var2 = var33;
            } else if (var2 != var33) {
               var2.addSuppressed(var33);
            }

            throw var2;
         }
      } catch (Exception var34) {
         logger.warn("Failed to obtain cluster hosts: " + this.clusterRef, var34);
         this.result = Collections.EMPTY_SET;
         return;
      }

      this.hostTasks = new HashMap(hosts.length);
      var2 = null;
      var3 = null;

      try {
         VcConnection vcConnection = this.vcClient.getConnection(this.clusterRef.getServerGuid());

         try {
            ManagedObjectReference[] var8 = hosts;
            int var7 = hosts.length;

            for(int var6 = 0; var6 < var7; ++var6) {
               ManagedObjectReference host = var8[var6];
               ManagedObjectReference internalSystemRef = this.vmodlHelper.getVsanInternalSystem(host);
               VsanInternalSystem internalSystem = (VsanInternalSystem)vcConnection.createStub(VsanInternalSystem.class, internalSystemRef);
               Future<String> compositeUuidsFuture = this.measure.newFuture("vsanUuids(" + host.getValue() + ")");
               internalSystem.queryPhysicalVsanDisks(VsanHostPropertyProviderAdapter.PHYSICAL_DISK_VIRTUAL_MAPPING_PROPERTIES, compositeUuidsFuture);
               this.hostTasks.put(host, compositeUuidsFuture);
            }
         } finally {
            if (vcConnection != null) {
               vcConnection.close();
            }

         }

      } catch (Throwable var36) {
         if (var2 == null) {
            var2 = var36;
         } else if (var2 != var36) {
            var2.addSuppressed(var36);
         }

         throw var2;
      }
   }

   public Set<String> prepareResult() {
      Set<String> vsanUuids = new HashSet();
      Iterator var3 = this.hostTasks.keySet().iterator();

      while(var3.hasNext()) {
         ManagedObjectReference hostRef = (ManagedObjectReference)var3.next();
         Future<String> task = (Future)this.hostTasks.get(hostRef);
         JsonNode hostJsonData = null;

         try {
            String json = (String)task.get();
            hostJsonData = Utils.getJsonRootNode(json);
         } catch (Exception var7) {
            logger.error("vSAN UUIDs omitted for disconnected host: " + hostRef, var7);
         }

         if (hostJsonData != null) {
            List<String> hostVsanUuids = hostJsonData.findValuesAsText("compositeUuid");
            vsanUuids.addAll(hostVsanUuids);
         }
      }

      return vsanUuids;
   }
}
