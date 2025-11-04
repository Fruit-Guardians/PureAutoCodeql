package com.vmware.vsan.client.util.retriever;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vmomi.core.Future;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectInformation;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectQuerySpec;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectSystem;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

class ObjectInformationDataRetriever extends AbstractAsyncDataRetriever<VsanObjectInformation[]> {
   private static final Log logger = LogFactory.getLog(ObjectInformationDataRetriever.class);
   private static final int UUID_BATCH_SIZE = 500;
   private List<Future<VsanObjectInformation[]>> futures;
   private Set<String> uuids;

   public ObjectInformationDataRetriever(ManagedObjectReference clusterRef, Measure measure, Set<String> uuids) {
      super(clusterRef, measure);
      this.uuids = uuids;
   }

   public void start() {
      List<Future<VsanObjectInformation[]>> objectInfoFutures = new ArrayList();
      VsanObjectSystem objectSystem = VsanProviderUtils.getVsanObjectSystem(this.clusterRef);
      if (!this.uuids.isEmpty()) {
         List<String> allUuids = new ArrayList(this.uuids);
         int hop = 0;
         int from = false;

         for(int to = 0; to < allUuids.size(); ++hop) {
            int from = hop * 500;
            to = Math.min((hop + 1) * 500, allUuids.size());
            Set<String> batch = new HashSet(allUuids.subList(from, to));
            Future<VsanObjectInformation[]> future = this.measure.newFuture("ObjectSystem.queryVsanObjectInformation");
            objectSystem.queryVsanObjectInformation(this.clusterRef, this.buildQuerySpecs(batch), future);
            objectInfoFutures.add(future);
         }

         logger.info("Requesting " + allUuids.size() + " UUIDs split into " + objectInfoFutures.size() + " separate calls.");
      }

      this.futures = objectInfoFutures;
   }

   public VsanObjectInformation[] prepareResult() throws ExecutionException, InterruptedException {
      List<VsanObjectInformation> objInfosList = new ArrayList();
      Iterator var3 = this.futures.iterator();

      while(var3.hasNext()) {
         Future<VsanObjectInformation[]> objectInfoFuture = (Future)var3.next();
         VsanObjectInformation[] result = (VsanObjectInformation[])objectInfoFuture.get();
         if (ArrayUtils.isEmpty(result)) {
            logger.warn("Found an empty VsanObjectInformation result. Probably something is wrong with the server.");
         } else {
            objInfosList.addAll(Arrays.asList(result));
         }
      }

      return (VsanObjectInformation[])objInfosList.toArray(new VsanObjectInformation[0]);
   }

   private VsanObjectQuerySpec[] buildQuerySpecs(Set<String> vsanObjectIds) {
      List<VsanObjectQuerySpec> vsanQuerySpecs = new ArrayList();
      Iterator var4 = vsanObjectIds.iterator();

      while(var4.hasNext()) {
         String objectId = (String)var4.next();
         vsanQuerySpecs.add(new VsanObjectQuerySpec(objectId, ""));
      }

      return (VsanObjectQuerySpec[])vsanQuerySpecs.toArray(new VsanObjectQuerySpec[0]);
   }
}
