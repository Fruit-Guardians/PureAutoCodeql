package com.vmware.vsan.client.services.dataprotection.archive;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsandp.binding.vim.vsandp.NfsArchivalStorageLocation;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ReplicaSeriesManager.SeriesFilterSpec;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ReplicaSeriesManager.SeriesQuery.Spec;
import com.vmware.vsan.client.services.dataprotection.ClusterDpConfigService;
import com.vmware.vsan.client.services.dataprotection.model.DatastoreData;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.DpClient;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ArchivalDpService {
   private static final Log logger = LogFactory.getLog(ArchivalDpService.class);
   private static final VsanProfiler profiler = new VsanProfiler(ArchivalDpService.class);
   @Autowired
   ClusterDpConfigService dpConfigService;
   @Autowired
   DpClient dpClient;

   @TsService
   public DatastoreData[] getRestoreDpDatastores(ManagedObjectReference clusterRef) throws Exception {
      return this.dpConfigService.getArchiveDpDatastores(clusterRef, true);
   }

   @TsService
   public List<ArchivalClusterData> getClusters(ManagedObjectReference param1, String param2) throws Exception {
      // $FF: Couldn't be decompiled
   }

   @TsService
   public List<ArchivalProtectionData> getClusterProtections(ManagedObjectReference param1, ArchivalClusterData param2, String param3) throws Exception {
      // $FF: Couldn't be decompiled
   }

   private Spec prepareSeriesQuerySpec(ArchivalClusterData cluster, String datastoreUrl) {
      SeriesFilterSpec seriesFilter = new SeriesFilterSpec();
      seriesFilter.clusterOwner = cluster.uuid;
      Spec querySpec = new Spec();
      querySpec.seriesFilter = seriesFilter;
      querySpec.location = new NfsArchivalStorageLocation(datastoreUrl);
      return querySpec;
   }
}
