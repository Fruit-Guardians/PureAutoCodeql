package com.vmware.vsan.client.services.virtualobjects;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.host.VsanInternalSystem;
import com.vmware.vim.binding.vim.host.VsanSystem;
import com.vmware.vim.binding.vim.vm.ConfigInfo;
import com.vmware.vim.binding.vim.vm.SnapshotInfo;
import com.vmware.vim.binding.vim.vm.SnapshotTree;
import com.vmware.vim.binding.vim.vm.device.VirtualDevice;
import com.vmware.vim.binding.vim.vm.device.VirtualDisk;
import com.vmware.vim.binding.vim.vsan.host.DiskResult;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vmomi.core.Future;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectIdentity;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectIdentityAndHealth;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectInformation;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterConfigSystem;
import com.vmware.vim.vsan.binding.vim.vsan.ConfigInfoEx;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.services.stretchedcluster.ConfigureStretchedClusterService;
import com.vmware.vsan.client.services.stretchedcluster.VsanHostsResult;
import com.vmware.vsan.client.services.virtualobjects.data.VirtualObjectModel;
import com.vmware.vsan.client.services.virtualobjects.data.VirtualObjectModelFactory;
import com.vmware.vsan.client.services.virtualobjects.data.VirtualObjectPlacementModel;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsan.client.util.retriever.VsanAsyncDataRetriever;
import com.vmware.vsan.client.util.retriever.VsanDataRetrieverFactory;
import com.vmware.vsphere.client.vsan.base.data.VsanObject;
import com.vmware.vsphere.client.vsan.base.impl.VsanComponentsProvider;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.util.DataServiceResponse;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsan.util.Utils;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Map.Entry;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class VirtualObjectsService {
   private static final Log logger = LogFactory.getLog(VirtualObjectsService.class);
   private static final int QUERY_VSAN_OBJECTS_CHUNK_SIZE = 500;
   private static final String PROP_SNAPSHOT = "snapshot";
   private static final String PROP_CONFIG = "config";
   private static final String[] PHYSICAL_PLACEMENT_HOST_PROPERTIES = new String[]{"name", "primaryIconId", "config.vsanHostConfig.clusterInfo.nodeUuid", "config.vsanHostConfig.faultDomainInfo.name"};
   @Autowired
   private VmodlHelper vmodlHelper;
   @Autowired
   private VcClient vcClient;
   @Autowired
   private VirtualObjectModelFactory voModelFactory;
   @Autowired
   private ConfigureStretchedClusterService stretchedClusterService;
   @Autowired
   private VsanDataRetrieverFactory dataRetrieverFactory;

   @TsService
   public List<VirtualObjectModel> listVirtualObjects(ManagedObjectReference clusterRef) throws Exception {
      Throwable var2 = null;
      Object var3 = null;

      try {
         Measure measure = new Measure("Collect Virtual Objects for cluster");

         Throwable var10000;
         label317: {
            List var34;
            boolean var10001;
            try {
               VsanAsyncDataRetriever dataRetriever = this.dataRetrieverFactory.createVsanAsyncDataRetriever(measure, clusterRef).loadIscsiTargets().loadIscsiLuns().loadFileShares().loadObjectIdentities().loadClusterUuids().loadStoragePolicies();
               Set<String> vsanUuids = dataRetriever.getClusterUuids();
               VsanObjectIdentityAndHealth identities = dataRetriever.getObjectIdentities();
               Set<ManagedObjectReference> vmRefs = new HashSet();
               if (!ArrayUtils.isEmpty(identities.identities)) {
                  VsanObjectIdentity[] var12;
                  int var11 = (var12 = identities.identities).length;

                  for(int var10 = 0; var10 < var11; ++var10) {
                     VsanObjectIdentity id = var12[var10];
                     if (id.vm != null) {
                        vmRefs.add(id.vm);
                        vsanUuids.add(id.uuid);
                     }
                  }
               }

               if (!VsanCapabilityUtils.isLocalDataProtectionSupported(clusterRef)) {
                  dataRetriever.loadObjectInformation(vsanUuids);
               }

               List virtualObjectModels = this.listVirtualObjects(clusterRef, vsanUuids, dataRetriever, vmRefs, measure);

               try {
                  virtualObjectModels.addAll(this.voModelFactory.buildIscsiTargets(dataRetriever.getIscsiTargets(), dataRetriever.getIscsiLuns(), dataRetriever.getStoragePolicies()));
               } catch (Exception var29) {
                  logger.warn("Failed to list iSCSI targets. Returning partial results.");
               }

               try {
                  virtualObjectModels.addAll(this.voModelFactory.buildFileShares(dataRetriever.getFileShares(), identities, dataRetriever.getObjectInformation(), dataRetriever.getStoragePolicies()));
               } catch (Exception var28) {
                  logger.warn("Failed to list File Shares. Returning partial results.");
               }

               var34 = virtualObjectModels;
            } catch (Throwable var31) {
               var10000 = var31;
               var10001 = false;
               break label317;
            }

            if (measure != null) {
               measure.close();
            }

            label302:
            try {
               return var34;
            } catch (Throwable var30) {
               var10000 = var30;
               var10001 = false;
               break label302;
            }
         }

         var2 = var10000;
         if (measure != null) {
            measure.close();
         }

         throw var2;
      } catch (Throwable var32) {
         if (var2 == null) {
            var2 = var32;
         } else if (var2 != var32) {
            var2.addSuppressed(var32);
         }

         throw var2;
      }
   }

   public List<VirtualObjectModel> listVmVirtualObjects(ManagedObjectReference clusterRef, ManagedObjectReference vmRef, Set<String> vmObjectUuids) throws Exception {
      Throwable var4 = null;
      Object var5 = null;

      try {
         Measure measure = new Measure("Collect Virtual Objects for specified VM");

         Throwable var10000;
         label197: {
            boolean var10001;
            List var21;
            try {
               VsanAsyncDataRetriever dataRetriever = this.dataRetrieverFactory.createVsanAsyncDataRetriever(measure, clusterRef).loadObjectIdentities().loadStoragePolicies();
               if (!VsanCapabilityUtils.isLocalDataProtectionSupported(clusterRef)) {
                  dataRetriever.loadObjectInformation(vmObjectUuids);
               }

               Set<ManagedObjectReference> vmRefs = new HashSet();
               vmRefs.add(vmRef);
               var21 = this.listVirtualObjects(clusterRef, vmObjectUuids, dataRetriever, vmRefs, measure);
            } catch (Throwable var19) {
               var10000 = var19;
               var10001 = false;
               break label197;
            }

            if (measure != null) {
               measure.close();
            }

            label186:
            try {
               return var21;
            } catch (Throwable var18) {
               var10000 = var18;
               var10001 = false;
               break label186;
            }
         }

         var4 = var10000;
         if (measure != null) {
            measure.close();
         }

         throw var4;
      } catch (Throwable var20) {
         if (var4 == null) {
            var4 = var20;
         } else if (var4 != var20) {
            var4.addSuppressed(var20);
         }

         throw var4;
      }
   }

   private List<VirtualObjectModel> listVirtualObjects(ManagedObjectReference clusterRef, Set<String> vsanUuids, VsanAsyncDataRetriever dataRetriever, Set<ManagedObjectReference> vmRefs, Measure measure) throws Exception {
      Multimap<ManagedObjectReference, ConfigInfo> vmSnapshots = HashMultimap.create();
      Map vmProperties;
      VsanObjectInformation[] objInfos;
      if (vmRefs.isEmpty()) {
         vmProperties = Collections.emptyMap();
      } else {
         vmSnapshots = this.listVmSnapshots((ManagedObjectReference[])vmRefs.toArray(new ManagedObjectReference[0]), measure);
         Throwable var8 = null;
         objInfos = null;

         try {
            Measure vmProps = measure.start("ds(" + (vmRefs.size() == 1 ? ((ManagedObjectReference)vmRefs.iterator().next()).getValue() : vmRefs.size() + "vms") + ")");

            try {
               vmProperties = QueryUtil.getProperties((ManagedObjectReference[])vmRefs.toArray(new ManagedObjectReference[0]), new String[]{"name", "primaryIconId", "config.hardware.device"}).getMap();
            } finally {
               if (vmProps != null) {
                  vmProps.close();
               }

            }
         } catch (Throwable var17) {
            if (var8 == null) {
               var8 = var17;
            } else if (var8 != var17) {
               var8.addSuppressed(var17);
            }

            throw var8;
         }
      }

      VsanObjectIdentityAndHealth identities = dataRetriever.getObjectIdentities();
      if (!VsanCapabilityUtils.isLocalDataProtectionSupported(clusterRef)) {
         objInfos = dataRetriever.getObjectInformation();
      } else {
         objInfos = new VsanObjectInformation[0];
      }

      Map<String, String> storagePolicies = dataRetriever.getStoragePolicies();
      List<VirtualObjectModel> models = new ArrayList();
      if (!vmRefs.isEmpty()) {
         models.addAll(this.voModelFactory.buildVms(identities, vmRefs, objInfos, vmProperties, (Multimap)vmSnapshots, storagePolicies));
      }

      models.addAll(this.voModelFactory.buildFcds(identities, objInfos, storagePolicies));
      models.addAll(this.voModelFactory.buildOthers(vsanUuids, identities, objInfos, storagePolicies));
      return models;
   }

   public Collection<ConfigInfo> listVmSnapshots(ManagedObjectReference vmRef, Measure measure) {
      return this.listVmSnapshots(new ManagedObjectReference[]{vmRef}, measure).get(vmRef);
   }

   public Multimap<ManagedObjectReference, ConfigInfo> listVmSnapshots(ManagedObjectReference[] vmsArray, Measure measure) {
      Multimap<ManagedObjectReference, ConfigInfo> vmToSnapshotConfig = HashMultimap.create();
      if (ArrayUtils.isEmpty(vmsArray)) {
         logger.warn("No VMs given.");
         return vmToSnapshotConfig;
      } else {
         HashMap vmToSnapshot = new HashMap();

         ManagedObjectReference vm;
         DataServiceResponse response;
         try {
            Throwable var5 = null;
            vm = null;

            try {
               Measure m = measure.start("ds(vm->snapshot)[" + vmsArray.length + "]");

               try {
                  response = QueryUtil.getProperties(vmsArray, new String[]{"snapshot"});
                  Map<ManagedObjectReference, Map<String, Object>> resultMap = response.getMap();
                  ManagedObjectReference[] var13 = vmsArray;
                  int var12 = vmsArray.length;

                  for(int var11 = 0; var11 < var12; ++var11) {
                     ManagedObjectReference vm = var13[var11];
                     Map<String, Object> result = (Map)resultMap.get(vm);
                     if (result != null) {
                        SnapshotInfo snapshotInfo = (SnapshotInfo)result.get("snapshot");
                        if (snapshotInfo != null) {
                           vmToSnapshot.put(vm, snapshotInfo);
                        }
                     }
                  }
               } finally {
                  if (m != null) {
                     m.close();
                  }

               }
            } catch (Throwable var46) {
               if (var5 == null) {
                  var5 = var46;
               } else if (var5 != var46) {
                  var5.addSuppressed(var46);
               }

               throw var5;
            }
         } catch (Exception var47) {
            logger.error("Cannot retrieve snapshots for VMs: ", var47);
            return vmToSnapshotConfig;
         }

         if (vmToSnapshot.isEmpty()) {
            logger.debug("None of the VMs has snapshots");
            return vmToSnapshotConfig;
         } else {
            Map<ManagedObjectReference, ManagedObjectReference> snapshotToVm = new HashMap();
            Iterator var50 = vmToSnapshot.keySet().iterator();

            while(true) {
               SnapshotInfo snapshotInfo;
               do {
                  if (!var50.hasNext()) {
                     if (snapshotToVm.isEmpty()) {
                        logger.debug("None of the VMs has snapshots");
                        return vmToSnapshotConfig;
                     }

                     ManagedObjectReference[] snapshots = (ManagedObjectReference[])snapshotToVm.keySet().toArray(new ManagedObjectReference[0]);

                     try {
                        Throwable var51 = null;
                        response = null;

                        try {
                           Measure m = measure.start("ds(snapshot->config)[" + snapshots.length + "]");

                           try {
                              DataServiceResponse response = QueryUtil.getProperties(snapshots, new String[]{"config"});
                              Map<ManagedObjectReference, Map<String, Object>> resultMap = response.getMap();
                              Iterator var59 = resultMap.entrySet().iterator();

                              while(var59.hasNext()) {
                                 Entry<ManagedObjectReference, Map<String, Object>> entry = (Entry)var59.next();
                                 ManagedObjectReference snapshot = (ManagedObjectReference)entry.getKey();
                                 Map<String, Object> propssss = (Map)entry.getValue();
                                 if (propssss != null) {
                                    ConfigInfo configInfo = (ConfigInfo)propssss.get("config");
                                    if (configInfo != null) {
                                       ManagedObjectReference vm = (ManagedObjectReference)snapshotToVm.get(snapshot);
                                       vmToSnapshotConfig.put(vm, configInfo);
                                    }
                                 }
                              }
                           } finally {
                              if (m != null) {
                                 m.close();
                              }

                           }
                        } catch (Throwable var43) {
                           if (var51 == null) {
                              var51 = var43;
                           } else if (var51 != var43) {
                              var51.addSuppressed(var43);
                           }

                           throw var51;
                        }
                     } catch (Exception var44) {
                        var44.printStackTrace();
                     }

                     return vmToSnapshotConfig;
                  }

                  vm = (ManagedObjectReference)var50.next();
                  snapshotInfo = (SnapshotInfo)vmToSnapshot.get(vm);
               } while(snapshotInfo.rootSnapshotList == null);

               LinkedList snapshotTrees = new LinkedList(Arrays.asList(snapshotInfo.rootSnapshotList));

               while(!snapshotTrees.isEmpty()) {
                  SnapshotTree tree = (SnapshotTree)snapshotTrees.poll();
                  snapshotToVm.put(tree.snapshot, vm);
                  if (ArrayUtils.isNotEmpty(tree.childSnapshotList)) {
                     snapshotTrees.addAll(Arrays.asList(tree.childSnapshotList));
                  }
               }
            }
         }
      }
   }

   private boolean isIscsiServiceEnabled(ManagedObjectReference clusterRef) throws Exception {
      if (!VsanCapabilityUtils.isIscsiTargetsSupportedOnVc(clusterRef)) {
         return false;
      } else {
         VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
         ConfigInfoEx configInfoEx = vsanConfigSystem.getConfigInfoEx(clusterRef);
         return configInfoEx.getIscsiConfig().enabled;
      }
   }

   @TsService
   public Map<String, Collection<VirtualObjectPlacementModel>> getPhysicalPlacement(ManagedObjectReference clusterRef, String[] objectIds) throws Exception {
      List<VsanObject> virtualObjects = new ArrayList();
      List<DiskResult> vsanDisks = new ArrayList();
      Throwable var6 = null;
      VirtualObjectPlacementModel.Builder builder = null;

      DataServiceResponse hostData;
      try {
         label952: {
            Measure measure = new Measure("Collecting placement details (" + objectIds.length + " objects)");

            Measure var10000;
            try {
               VsanHostsResult vsanHostsResult = this.stretchedClusterService.collectVsanHosts(clusterRef, true, measure);
               Map<ManagedObjectReference, Future<DiskResult[]>> hostDiskFutures = new HashMap();
               Iterator var12 = vsanHostsResult.getAll().iterator();

               Throwable var13;
               Measure dsProps;
               while(var12.hasNext()) {
                  ManagedObjectReference hostRef = (ManagedObjectReference)var12.next();
                  var13 = null;
                  dsProps = null;

                  try {
                     VcConnection conn = this.vcClient.getConnection(hostRef.getServerGuid());

                     try {
                        VsanSystem vsanSystem = VsanProviderUtils.getHostVsanSystem(hostRef, conn);
                        Future<DiskResult[]> diskDataFuture = measure.newFuture("VsanSystem.queryDisksForVsan");
                        vsanSystem.queryDisksForVsan((String[])null, diskDataFuture);
                        hostDiskFutures.put(hostRef, diskDataFuture);
                     } finally {
                        if (conn != null) {
                           conn.close();
                        }

                     }
                  } catch (Throwable var64) {
                     if (var13 == null) {
                        var13 = var64;
                     } else if (var13 != var64) {
                        var13.addSuppressed(var64);
                     }

                     throw var13;
                  }
               }

               Set<ManagedObjectReference> hosts = vsanHostsResult.connectedMembers;
               if (!CollectionUtils.isEmpty(hosts)) {
                  this.populateVirtualObjectsFromInternalSystem(objectIds, virtualObjects, measure, hosts);
                  Throwable var71 = null;
                  var13 = null;

                  try {
                     dsProps = measure.start("host(props)");

                     try {
                        hostData = QueryUtil.getProperties((ManagedObjectReference[])vsanHostsResult.getAll().toArray(new ManagedObjectReference[0]), PHYSICAL_PLACEMENT_HOST_PROPERTIES);
                     } finally {
                        if (dsProps != null) {
                           dsProps.close();
                        }

                     }
                  } catch (Throwable var62) {
                     if (var71 == null) {
                        var71 = var62;
                     } else if (var71 != var62) {
                        var71.addSuppressed(var62);
                     }

                     throw var71;
                  }

                  Iterator var73 = hostDiskFutures.keySet().iterator();

                  while(true) {
                     if (!var73.hasNext()) {
                        break label952;
                     }

                     ManagedObjectReference hostRef = (ManagedObjectReference)var73.next();
                     DiskResult[] diskData = null;

                     try {
                        diskData = (DiskResult[])((Future)hostDiskFutures.get(hostRef)).get();
                     } catch (Exception var60) {
                        logger.warn("Failed to list claimed disks for host: " + hostRef, var60);
                     }

                     if (diskData != null) {
                        vsanDisks.addAll(Arrays.asList(diskData));
                     }
                  }
               }

               new HashMap();
            } finally {
               var10000 = measure;
               if (measure != null) {
                  var10000 = measure;
                  measure.close();
               }

            }

            return var10000;
         }
      } catch (Throwable var66) {
         if (var6 == null) {
            var6 = var66;
         } else if (var6 != var66) {
            var6.addSuppressed(var66);
         }

         throw var6;
      }

      Multimap<String, VirtualObjectPlacementModel> result = HashMultimap.create();
      builder = new VirtualObjectPlacementModel.Builder(vsanDisks, hostData);
      Iterator var69 = virtualObjects.iterator();

      while(var69.hasNext()) {
         VsanObject vsanObject = (VsanObject)var69.next();
         result.putAll(vsanObject.vsanObjectUuid, builder.build(vsanObject));
      }

      return result.asMap();
   }

   private void populateVirtualObjectsFromInternalSystem(String[] objectIds, List<VsanObject> virtualObjects, Measure measure, Set<ManagedObjectReference> hosts) throws Exception {
      Queue chunks = this.chunkify(objectIds);

      while(!chunks.isEmpty()) {
         Map<Future<String>, List<String>> futures = new HashMap();
         Iterator var8 = hosts.iterator();

         Future future;
         List chunk;
         String errorMessage;
         while(var8.hasNext()) {
            ManagedObjectReference host = (ManagedObjectReference)var8.next();
            future = measure.newFuture("VsanInternalSystem.queryVsanObjects[" + host + "]");
            chunk = (List)chunks.poll();
            if (chunk == null) {
               break;
            }

            futures.put(future, chunk);
            logger.debug("Query UUIDs on " + host);
            Throwable var11 = null;
            errorMessage = null;

            try {
               VcConnection vc = this.vcClient.getConnection(host.getServerGuid());

               try {
                  VsanInternalSystem internalSystem = (VsanInternalSystem)vc.createStub(VsanInternalSystem.class, this.vmodlHelper.getVsanInternalSystem(host));
                  internalSystem.queryVsanObjects((String[])chunk.toArray(new String[chunk.size()]), future);
               } finally {
                  if (vc != null) {
                     vc.close();
                  }

               }
            } catch (Throwable var23) {
               if (var11 == null) {
                  var11 = var23;
               } else if (var11 != var23) {
                  var11.addSuppressed(var23);
               }

               throw var11;
            }
         }

         logger.debug("Waiting for the started requests to finish.");
         var8 = futures.entrySet().iterator();

         while(var8.hasNext()) {
            Entry<Future<String>, List<String>> entry = (Entry)var8.next();
            future = (Future)entry.getKey();
            chunk = (List)entry.getValue();

            try {
               String json = (String)future.get();
               List<VsanObject> vsanObjects = VsanComponentsProvider.VsanJsonParser.parseVsanObjects(json, chunk);
               virtualObjects.addAll(vsanObjects);
            } catch (SocketTimeoutException var21) {
               logger.error(var21);
               errorMessage = Utils.getLocalizedString("vsan.virtualObjects.error.timeout");
               throw new Exception(errorMessage);
            }
         }
      }

   }

   private Queue<List<String>> chunkify(String[] allUuids) {
      Queue<List<String>> chunks = new LinkedList();
      List<String> uuids = Arrays.asList(allUuids);
      int chunksCount = uuids.size() / 500;

      for(int i = 0; i < chunksCount; ++i) {
         int startingIndex = i * 500;
         List<String> subUuids = uuids.subList(startingIndex, startingIndex + 500);
         chunks.add(subUuids);
      }

      List<String> subUuids = uuids.subList(chunksCount * 500, uuids.size());
      chunks.add(subUuids);
      logger.debug("Splitting the UUIDs into " + chunks.size() + " chunks.");
      return chunks;
   }

   @TsService
   public VirtualDisk getDiskDetails(ManagedObjectReference vmRef, String diskId) throws Exception {
      VirtualDevice[] virtualDevices = (VirtualDevice[])QueryUtil.getProperty(vmRef, "config.hardware.device", (Object)null);
      VirtualDisk result = VirtualObjectsUtil.findDisk(virtualDevices, diskId);
      if (result == null) {
         throw new IllegalArgumentException("Disk not found: " + diskId);
      } else {
         return result;
      }
   }
}
