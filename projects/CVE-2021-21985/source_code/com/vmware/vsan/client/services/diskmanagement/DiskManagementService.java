package com.vmware.vsan.client.services.diskmanagement;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.HostSystem;
import com.vmware.vim.binding.vim.HostSystem.ConnectionState;
import com.vmware.vim.binding.vim.host.StorageDeviceInfo;
import com.vmware.vim.binding.vim.host.StorageSystem;
import com.vmware.vim.binding.vim.host.VsanInternalSystem;
import com.vmware.vim.binding.vim.host.VsanSystem;
import com.vmware.vim.binding.vim.host.PlugStoreTopology.Adapter;
import com.vmware.vim.binding.vim.host.PlugStoreTopology.Path;
import com.vmware.vim.binding.vim.host.PlugStoreTopology.Target;
import com.vmware.vim.binding.vim.vsan.host.ClusterStatus;
import com.vmware.vim.binding.vim.vsan.host.DiskResult;
import com.vmware.vim.binding.vim.vsan.host.DiskResult.State;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vmomi.core.Future;
import com.vmware.vim.vmomi.core.impl.BlockingFuture;
import com.vmware.vim.vsan.binding.vim.cluster.VSANWitnessHostInfo;
import com.vmware.vim.vsan.binding.vim.cluster.VsanCapability;
import com.vmware.vim.vsan.binding.vim.cluster.VsanCapabilitySystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcDiskManagementSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcStretchedClusterSystem;
import com.vmware.vim.vsan.binding.vim.vsan.host.DiskMapInfoEx;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vsan.client.services.stretchedcluster.ConfigureStretchedClusterService;
import com.vmware.vsan.client.services.stretchedcluster.VsanHostsResult;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsan.client.util.NoOpMeasure;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.base.data.VsanCapabilityData;
import com.vmware.vsphere.client.vsan.base.service.VsanService;
import com.vmware.vsphere.client.vsan.base.service.VsanServiceFactory;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.base.util.multithreading.VsanAsyncQueryUtils;
import com.vmware.vsphere.client.vsan.dataprovider.VsanHostPropertyProviderAdapter;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DiskManagementService {
   private static final Logger logger = LoggerFactory.getLogger(DiskManagementService.class);
   private static final String HOST_DISCONNECTED_MESSAGE_KEY = "com.vmware.vsan.diskmgmt.msg.hostnotconnected";
   @Autowired
   private VcClient vcClient;
   @Autowired
   private VmodlHelper vmodlHelper;
   @Autowired
   private VsanServiceFactory vsanServiceFactory;
   @Autowired
   private VsanHostPropertyProviderAdapter vsanPropertyProvider;
   @Autowired
   private ConfigureStretchedClusterService stretchedClusterService;

   @TsService
   public List<HostData> listHosts(ManagedObjectReference clusterRef) throws Throwable {
      Throwable var2 = null;
      Object var3 = null;

      try {
         Measure measure = new Measure("Collect Disk Mappings");

         Throwable var10000;
         label433: {
            VsanHostsResult vsanHosts;
            ArrayList allHosts;
            boolean var10001;
            label430: {
               List var48;
               try {
                  vsanHosts = this.stretchedClusterService.collectVsanHosts(clusterRef, true, measure);
                  allHosts = new ArrayList(vsanHosts.getAll());
                  if (!allHosts.isEmpty()) {
                     break label430;
                  }

                  var48 = Collections.EMPTY_LIST;
               } catch (Throwable var46) {
                  var10000 = var46;
                  var10001 = false;
                  break label433;
               }

               if (measure != null) {
                  measure.close();
               }

               return var48;
            }

            ArrayList var49;
            try {
               Map<ManagedObjectReference, Future<DiskMapInfoEx[]>> mappingsTasks = this.getDiskMappingsAsync(allHosts, measure);
               Map<ManagedObjectReference, Future<DiskResult[]>> vsanDisksTasks = this.getVsanDisksAsync(allHosts, measure);
               Map<ManagedObjectReference, Future<String>> diskHealthAndVersionTasks = this.getDiskHealthAndVersionAsync(allHosts, measure);
               Map<ManagedObjectReference, Future<StorageDeviceInfo>> hostDeviceInfosTasks = this.getHostDeviceInfosAsync(allHosts, measure);
               Map<ManagedObjectReference, Future<ClusterStatus>> hostClusterStatusTasks = this.getHostClusterStatusAsync(allHosts, measure);
               Future<VsanCapability[]> hostCapabilitiesTask = this.getHostCapabilitiesAsync(clusterRef, allHosts, measure);
               Measure hostPropsMeasure = measure.start("DS(hostProps)");
               Map<Object, Map<String, Object>> dsProperties = QueryUtil.getProperties((ManagedObjectReference[])allHosts.toArray(new ManagedObjectReference[0]), HostData.DS_HOST_PROPERTIES).getMap();
               hostPropsMeasure.close();
               Map<ManagedObjectReference, DiskMapInfoEx[]> mappings = VsanAsyncQueryUtils.awaitAll(mappingsTasks, new DiskManagementService.DiskMappingsAwaitor((DiskManagementService.DiskMappingsAwaitor)null));
               Map<ManagedObjectReference, DiskResult[]> vsanDisks = await(vsanDisksTasks);
               Map<ManagedObjectReference, String> diskHealthAndVersions = await(diskHealthAndVersionTasks);
               Map<ManagedObjectReference, StorageDeviceInfo> deviceInfos = await(hostDeviceInfosTasks);
               Map<ManagedObjectReference, ClusterStatus> hostClusterStates = await(hostClusterStatusTasks);
               Map<ManagedObjectReference, VsanCapabilityData> hostCapabilities = HostData.mapCapabilities((VsanCapability[])await(ImmutableMap.of(clusterRef, hostCapabilitiesTask)).get(clusterRef), clusterRef);
               Collection<Set<String>> networkPartitions = new HashSet();
               Iterator var23 = hostClusterStates.values().iterator();

               while(var23.hasNext()) {
                  ClusterStatus status = (ClusterStatus)var23.next();
                  if (status != null && status.memberUuid != null) {
                     networkPartitions.add(Sets.newHashSet(status.memberUuid));
                  }
               }

               List<HostData> result = new ArrayList();
               Iterator var24 = allHosts.iterator();

               while(var24.hasNext()) {
                  ManagedObjectReference hostRef = (ManagedObjectReference)var24.next();
                  ClusterStatus status = (ClusterStatus)hostClusterStates.get(hostRef);
                  Integer partitionGroup = null;
                  if (status != null) {
                     partitionGroup = HostData.getNetworkPartitionGroup(status.nodeUuid, networkPartitions);
                  }

                  HostData hostData = HostData.create(hostRef, vsanHosts.witnesses.contains(hostRef), (Map)dsProperties.get(hostRef), (DiskMapInfoEx[])mappings.get(hostRef), (DiskResult[])vsanDisks.get(hostRef), (String)diskHealthAndVersions.get(hostRef), (StorageDeviceInfo)deviceInfos.get(hostRef), status, partitionGroup, (VsanCapabilityData)hostCapabilities.get(hostRef));
                  result.add(hostData);
               }

               var49 = result;
            } catch (Throwable var45) {
               var10000 = var45;
               var10001 = false;
               break label433;
            }

            if (measure != null) {
               measure.close();
            }

            label412:
            try {
               return var49;
            } catch (Throwable var44) {
               var10000 = var44;
               var10001 = false;
               break label412;
            }
         }

         var2 = var10000;
         if (measure != null) {
            measure.close();
         }

         throw var2;
      } catch (Throwable var47) {
         if (var2 == null) {
            var2 = var47;
         } else if (var2 != var47) {
            var2.addSuppressed(var47);
         }

         throw var2;
      }
   }

   @TsService
   public boolean hasNetworkPartitionsOrDisconnectedMembers(ManagedObjectReference clusterRef) throws Exception {
      VsanVcStretchedClusterSystem stretchedClusterSystem = VsanProviderUtils.getVcStretchedClusterSystem(clusterRef);
      Future<VSANWitnessHostInfo[]> witnessHostsFuture = new BlockingFuture();
      stretchedClusterSystem.getWitnessHosts(clusterRef, witnessHostsFuture);
      HashSet hosts = new HashSet();

      try {
         PropertyValue[] hostProps = QueryUtil.getPropertiesForRelatedObjects(clusterRef, "host", HostSystem.class.getSimpleName(), new String[]{"runtime.connectionState"}).getPropertyValues();
         if (ArrayUtils.isEmpty(hostProps)) {
            return false;
         }

         PropertyValue[] var9 = hostProps;
         int var8 = hostProps.length;

         for(int var7 = 0; var7 < var8; ++var7) {
            PropertyValue val = var9[var7];
            if (val.propertyName.equals("runtime.connectionState")) {
               if (!ConnectionState.connected.equals(val.value)) {
                  return true;
               }

               ManagedObjectReference hostRef = (ManagedObjectReference)val.resourceObject;
               hosts.add(hostRef);
            }
         }
      } catch (Exception var11) {
         logger.warn("Failed to list hosts, presumably empty cluster.", var11);
         return false;
      }

      return this.hasNetworkPartition(clusterRef, witnessHostsFuture, hosts);
   }

   public boolean hasNetworkPartition(ManagedObjectReference clusterRef, Future<VSANWitnessHostInfo[]> witnessHostsFuture, Set<ManagedObjectReference> hosts) {
      VSANWitnessHostInfo[] witnesses = (VSANWitnessHostInfo[])await(ImmutableMap.of(clusterRef, witnessHostsFuture)).get(clusterRef);
      if (witnesses != null) {
         VSANWitnessHostInfo[] var8 = witnesses;
         int var7 = witnesses.length;

         for(int var6 = 0; var6 < var7; ++var6) {
            VSANWitnessHostInfo info = var8[var6];
            ManagedObjectReference witnessRef = new ManagedObjectReference(info.host.getType(), info.host.getValue(), clusterRef.getServerGuid());
            hosts.add(witnessRef);
         }
      }

      Map<ManagedObjectReference, ClusterStatus> hostClusterStates = await(this.getHostClusterStatusAsync(new ArrayList(hosts), new NoOpMeasure()));
      Iterator var12 = hostClusterStates.values().iterator();

      int members;
      do {
         if (!var12.hasNext()) {
            return false;
         }

         ClusterStatus status = (ClusterStatus)var12.next();
         members = status.memberUuid != null ? status.memberUuid.length : 0;
      } while(members == hosts.size());

      return true;
   }

   @TsService
   public List<DiskData> listEligibleDisks(ManagedObjectReference hostRef, Boolean flashOnly) throws Throwable {
      Throwable var3 = null;
      Object var4 = null;

      try {
         Measure measure = new Measure("Collect Eligible Disks");

         Throwable var10000;
         label251: {
            boolean var10001;
            ArrayList var30;
            try {
               List<ManagedObjectReference> hostList = Arrays.asList(hostRef);
               Map<ManagedObjectReference, Future<DiskResult[]>> vsanDisksTasks = this.getVsanDisksAsync(hostList, measure);
               Map<ManagedObjectReference, Future<StorageDeviceInfo>> hostDeviceInfosTasks = this.getHostDeviceInfosAsync(hostList, measure);
               Map<ManagedObjectReference, DiskResult[]> vsanDisks = await(vsanDisksTasks);
               Map<ManagedObjectReference, StorageDeviceInfo> deviceInfos = await(hostDeviceInfosTasks);
               List<DiskData> availableDisks = new ArrayList();
               DiskResult[] var15;
               int var14 = (var15 = (DiskResult[])vsanDisks.get(hostRef)).length;
               int var13 = 0;

               while(true) {
                  if (var13 >= var14) {
                     var30 = availableDisks;
                     break;
                  }

                  DiskResult d = var15[var13];
                  if ((flashOnly == null || d.disk.ssd == flashOnly) && State.eligible == State.valueOf(d.state)) {
                     StorageDeviceInfo deviceInfo = (StorageDeviceInfo)deviceInfos.get(hostRef);
                     Map<String, Path> disksMap = DiskData.mapDiskPaths(deviceInfo);
                     availableDisks.add(DiskData.fromScsiDisk(d.disk, (String)null, false, (String)null, (Target)DiskData.mapAvailableTargets(deviceInfo).get(((Path)disksMap.get(d.disk.uuid)).target), (Adapter)DiskData.mapAvailableAdapters(deviceInfo).get(((Path)disksMap.get(d.disk.uuid)).adapter)));
                  }

                  ++var13;
               }
            } catch (Throwable var28) {
               var10000 = var28;
               var10001 = false;
               break label251;
            }

            if (measure != null) {
               measure.close();
            }

            label239:
            try {
               return var30;
            } catch (Throwable var27) {
               var10000 = var27;
               var10001 = false;
               break label239;
            }
         }

         var3 = var10000;
         if (measure != null) {
            measure.close();
         }

         throw var3;
      } catch (Throwable var29) {
         if (var3 == null) {
            var3 = var29;
         } else if (var3 != var29) {
            var3.addSuppressed(var29);
         }

         throw var3;
      }
   }

   public Map<ManagedObjectReference, Future<DiskMapInfoEx[]>> getDiskMappingsAsync(List<ManagedObjectReference> allHosts, Measure measure) {
      Map<ManagedObjectReference, Future<DiskMapInfoEx[]>> result = new HashMap();
      Iterator var5 = allHosts.iterator();

      while(var5.hasNext()) {
         ManagedObjectReference hostRef = (ManagedObjectReference)var5.next();
         VsanVcDiskManagementSystem diskSystem = VsanProviderUtils.getVcDiskManagementSystem(hostRef);
         Future<DiskMapInfoEx[]> future = measure.newFuture("DiskMapInfoEx[]");
         diskSystem.queryDiskMappings(hostRef, future);
         result.put(hostRef, future);
      }

      return result;
   }

   public Map<ManagedObjectReference, Future<DiskResult[]>> getVsanDisksAsync(List<ManagedObjectReference> allHosts, Measure measure) {
      Map<ManagedObjectReference, Future<DiskResult[]>> tasks = Maps.newHashMap();
      Iterator var5 = allHosts.iterator();

      while(var5.hasNext()) {
         ManagedObjectReference hostRef = (ManagedObjectReference)var5.next();

         try {
            Throwable var6 = null;
            Object var7 = null;

            try {
               VcConnection conn = this.vcClient.getConnection(hostRef.getServerGuid());

               try {
                  VsanSystem vsanSystem = conn.getHostVsanSystem(hostRef);
                  Future<DiskResult[]> future = measure.newFuture("DiskResult[]");
                  vsanSystem.queryDisksForVsan((String[])null, future);
                  tasks.put(hostRef, future);
               } finally {
                  if (conn != null) {
                     conn.close();
                  }

               }
            } catch (Throwable var18) {
               if (var6 == null) {
                  var6 = var18;
               } else if (var6 != var18) {
                  var6.addSuppressed(var18);
               }

               throw var6;
            }
         } catch (Exception var19) {
            logger.warn("Unable to extract disks data for host (probably witness): " + hostRef);
         }
      }

      return tasks;
   }

   private Map<ManagedObjectReference, Future<ClusterStatus>> getHostClusterStatusAsync(List<ManagedObjectReference> allHosts, Measure measure) {
      Map<ManagedObjectReference, Future<ClusterStatus>> tasks = Maps.newHashMap();
      Iterator var5 = allHosts.iterator();

      while(var5.hasNext()) {
         ManagedObjectReference hostRef = (ManagedObjectReference)var5.next();

         try {
            Throwable var6 = null;
            Object var7 = null;

            try {
               VcConnection conn = this.vcClient.getConnection(hostRef.getServerGuid());

               try {
                  VsanSystem vsanSystem = conn.getHostVsanSystem(hostRef);
                  Future<ClusterStatus> future = measure.newFuture("ClusterStatus");
                  vsanSystem.queryHostStatus(future);
                  tasks.put(hostRef, future);
               } finally {
                  if (conn != null) {
                     conn.close();
                  }

               }
            } catch (Throwable var18) {
               if (var6 == null) {
                  var6 = var18;
               } else if (var6 != var18) {
                  var6.addSuppressed(var18);
               }

               throw var6;
            }
         } catch (Exception var19) {
            logger.warn("Unable to extract disks data for host (probably witness): " + hostRef);
         }
      }

      return tasks;
   }

   private Map<ManagedObjectReference, Future<String>> getDiskHealthAndVersionAsync(List<ManagedObjectReference> allHosts, Measure measure) {
      Map<ManagedObjectReference, Future<String>> tasks = new HashMap();
      Iterator var5 = allHosts.iterator();

      while(var5.hasNext()) {
         ManagedObjectReference hostRef = (ManagedObjectReference)var5.next();
         ManagedObjectReference vsanInternalSystemRef = this.vmodlHelper.getVsanInternalSystem(hostRef);
         VsanService vsanService = this.vsanServiceFactory.getService(hostRef.getServerGuid());
         VsanInternalSystem vsanInternalSystem = (VsanInternalSystem)vsanService.getManagedObject(vsanInternalSystemRef);
         Future<String> future = measure.newFuture("_diskHealth");
         vsanInternalSystem.queryPhysicalVsanDisks(new String[]{"disk_health", "formatVersion", "publicFormatVersion", "self_only"}, future);
         tasks.put(hostRef, future);
      }

      return tasks;
   }

   private Map<ManagedObjectReference, Future<StorageDeviceInfo>> getHostDeviceInfosAsync(List<ManagedObjectReference> allHosts, Measure measure) {
      Map<ManagedObjectReference, Future<StorageDeviceInfo>> tasks = new HashMap();
      Iterator var5 = allHosts.iterator();

      while(var5.hasNext()) {
         ManagedObjectReference hostRef = (ManagedObjectReference)var5.next();
         Throwable var6 = null;
         Object var7 = null;

         try {
            VcConnection vcConnection = this.vcClient.getConnection(hostRef.getServerGuid());

            try {
               StorageSystem storageSystem = (StorageSystem)vcConnection.createStub(StorageSystem.class, VmodlHelper.getStorageSystem(hostRef));
               Future<StorageDeviceInfo> future = measure.newFuture("StorageDeviceInfo");
               storageSystem.getStorageDeviceInfo(future);
               tasks.put(hostRef, future);
            } finally {
               if (vcConnection != null) {
                  vcConnection.close();
               }

            }
         } catch (Throwable var16) {
            if (var6 == null) {
               var6 = var16;
            } else if (var6 != var16) {
               var6.addSuppressed(var16);
            }

            throw var6;
         }
      }

      return tasks;
   }

   private Future<VsanCapability[]> getHostCapabilitiesAsync(ManagedObjectReference clusterRef, List<ManagedObjectReference> allHosts, Measure measure) {
      VsanCapabilitySystem capabilitySystem = VsanProviderUtils.getVsanCapabilitySystem(clusterRef);
      Future<VsanCapability[]> future = measure.newFuture("VsanCapability[]");
      capabilitySystem.getCapabilities((ManagedObjectReference[])allHosts.toArray(new ManagedObjectReference[allHosts.size()]), future);
      return future;
   }

   private static <T> Map<ManagedObjectReference, T> await(Map<ManagedObjectReference, Future<T>> tasks) {
      return VsanAsyncQueryUtils.awaitAll(tasks, new DiskManagementService.TaskAwaitor((DiskManagementService.TaskAwaitor)null));
   }

   private class DiskMappingsAwaitor implements Function<Entry<ManagedObjectReference, Future<DiskMapInfoEx[]>>, DiskMapInfoEx[]> {
      private DiskMappingsAwaitor() {
      }

      public DiskMapInfoEx[] apply(Entry<ManagedObjectReference, Future<DiskMapInfoEx[]>> future) {
         try {
            return (DiskMapInfoEx[])((Future)future.getValue()).get();
         } catch (Exception var3) {
            DiskManagementService.logger.warn("Cannot query host's disk mappings from VsanVcDiskManagementSystem; Trying to get disk mappings from host's storageInfo property", var3);
            return VsanHostPropertyProviderAdapter.getDiskMapingFallback((ManagedObjectReference)future.getKey());
         }
      }

      // $FF: synthetic method
      DiskMappingsAwaitor(DiskManagementService.DiskMappingsAwaitor var2) {
         this();
      }
   }

   private static class TaskAwaitor<T> implements Function<Entry<ManagedObjectReference, Future<T>>, T> {
      private TaskAwaitor() {
      }

      public T apply(Entry<ManagedObjectReference, Future<T>> future) {
         try {
            return ((Future)future.getValue()).get();
         } catch (Exception var3) {
            DiskManagementService.logger.warn("Cannot execute task: ", var3);
            return null;
         }
      }

      // $FF: synthetic method
      TaskAwaitor(DiskManagementService.TaskAwaitor var1) {
         this();
      }
   }
}
