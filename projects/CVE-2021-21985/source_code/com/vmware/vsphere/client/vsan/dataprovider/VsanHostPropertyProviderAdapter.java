package com.vmware.vsphere.client.vsan.dataprovider;

import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.HostSystem;
import com.vmware.vim.binding.vim.HostSystem.ConnectionState;
import com.vmware.vim.binding.vim.HostSystem.PowerState;
import com.vmware.vim.binding.vim.host.ScsiDisk;
import com.vmware.vim.binding.vim.host.VsanInternalSystem;
import com.vmware.vim.binding.vim.host.VsanSystem;
import com.vmware.vim.binding.vim.vsan.host.ClusterStatus;
import com.vmware.vim.binding.vim.vsan.host.DiskMapInfo;
import com.vmware.vim.binding.vim.vsan.host.DiskMapping;
import com.vmware.vim.binding.vim.vsan.host.DiskResult;
import com.vmware.vim.binding.vim.vsan.host.VsanRuntimeInfo;
import com.vmware.vim.binding.vim.vsan.host.ConfigInfo.StorageInfo;
import com.vmware.vim.binding.vim.vsan.host.DiskResult.State;
import com.vmware.vim.binding.vim.vsan.host.VsanRuntimeInfo.DiskIssue;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcDiskManagementSystem;
import com.vmware.vim.vsan.binding.vim.vsan.host.DiskMapInfoEx;
import com.vmware.vise.data.query.DataServiceExtensionRegistry;
import com.vmware.vise.data.query.PropertyProviderAdapter;
import com.vmware.vise.data.query.PropertyRequestSpec;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.ResultSet;
import com.vmware.vise.data.query.TypeInfo;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsphere.client.vsan.base.util.BaseUtils;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.base.util.multithreading.VsanAsyncQueryUtils;
import com.vmware.vsphere.client.vsan.data.ClaimOption;
import com.vmware.vsphere.client.vsan.data.DiskLocalityType;
import com.vmware.vsphere.client.vsan.data.VsanDiskAndGroupData;
import com.vmware.vsphere.client.vsan.data.VsanDiskData;
import com.vmware.vsphere.client.vsan.data.VsanDiskGroupData;
import com.vmware.vsphere.client.vsan.data.VsanHostData;
import com.vmware.vsphere.client.vsan.data.VsanSemiAutoClaimDisksData;
import com.vmware.vsphere.client.vsan.data.VsanVirtualPhysicalMappingData;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsan.util.Utils;
import com.vmware.vsphere.client.vsan.util.VsanAllFlashClaimOptionRecommender;
import com.vmware.vsphere.client.vsan.util.VsanHybridClaimOptionRecommender;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class VsanHostPropertyProviderAdapter implements PropertyProviderAdapter {
   public static final String VSAN_SEMI_AUTO_DISKS_PROPERTY_NAME = "vsanSemiAutoClaimDisksData";
   public static final String VSAN_VIRTUAL_PHYSICAL_MAPPING_DATA_PROPERTY = "vsanVirtualPhysicalMappingData";
   public static final String FAULT_DOMAIN_PROPERTY = "config.vsanHostConfig.faultDomainInfo.name";
   public static final String VSAN_HOST_CLUSTER_STATUS_PROPERTY = "vsanHostClusterStatus";
   public static final String VSAN_HOST_DISKS_FOR_VSAN_PROPERTY = "vsanHostDisksForVsan";
   public static final String VSAN_PHYSICAL_DISK_HEALTH_AND_VERSION_PROPERTY = "vsanPhysicalDiskHealthAndVersion";
   public static final String HOST_VSAN_STORAGE_INFO_PROPERTY = "config.vsanHostConfig.storageInfo";
   public static final String HOST_VSAN_RUNTIME_INFO = "runtime.vsanRuntimeInfo";
   public static final String STORAGE_ADAPTER_DEVICES = "storageAdapterDevices";
   public static final String HOST_OPERATIONAL_PROPERTY_NAME = "isHostOperational";
   public static final String CONNECTION_STATE_PROPERTY = "runtime.connectionState";
   public static final String POWER_STATE_PROPERTY = "runtime.powerState";
   public static final String MAINTENANCE_MODE_PROPERTY = "runtime.inMaintenanceMode";
   public static final String DISABLED_METHODS_PROPERTY = "disabledMethod";
   public static final String ENTER_MAINTENANCE_MODE_DISABLED_METHOD = "EnterMaintenanceMode_Task";
   public static final String EXIT_MAINTENANCE_MODE_DISABLED_METHOD = "ExitMaintenanceMode_Task";
   public static final String IS_ALL_FLASH_SUPPORTED_PROPERTY = "isAllFlashSupported";
   public static final String[] PHYSICAL_DISK_HEALTH_AND_VERSION_PROPERTIES = new String[]{"disk_health", "formatVersion", "publicFormatVersion", "self_only"};
   public static final String[] PHYSICAL_DISK_VIRTUAL_MAPPING_PROPERTIES = new String[]{"lsom_objects", "lsom_objects_count", "disk_health", "capacityReserved", "capacityUsed", "self_only"};
   public static final String HOST_PARENT_PROPERTY = "parent";
   private static final Log _logger = LogFactory.getLog(VsanHostPropertyProviderAdapter.class);
   private static final VsanProfiler _profiler = new VsanProfiler(VsanHostPropertyProviderAdapter.class);
   private final VcClient _vcClient;

   public VsanHostPropertyProviderAdapter(DataServiceExtensionRegistry registry, VcClient vcClient) {
      Validate.notNull(registry);
      this._vcClient = vcClient;
      TypeInfo hostInfo = new TypeInfo();
      hostInfo.type = HostSystem.class.getSimpleName();
      hostInfo.properties = new String[]{"vsanDisksAndGroupsData", "vsanDiskMapData", "vsanSemiAutoClaimDisksData", "vsanVirtualPhysicalMappingData", "vsanHostClusterStatus", "vsanHostDisksForVsan", "vsanPhysicalDiskHealthAndVersion", "vsanStorageAdapterDevices", "vsanPhysicalDiskVirtualMapping", "isHostOperational", "isAllFlashSupported"};
      TypeInfo[] providedProperties = new TypeInfo[]{hostInfo};
      registry.registerDataAdapter(this, providedProperties);
   }

   public ResultSet getProperties(PropertyRequestSpec propertyRequest) {
      if (!this.isValidRequest(propertyRequest)) {
         ResultSet result = new ResultSet();
         result.totalMatchedObjectCount = 0;
         return result;
      } else {
         Set<ManagedObjectReference> allHosts = this.getHosts(propertyRequest.objects);
         ManagedObjectReference[] hosts = (ManagedObjectReference[])allHosts.toArray(new ManagedObjectReference[0]);
         boolean isAllFlashSupported = false;
         boolean allFlashCheckRequested = QueryUtil.isAnyPropertyRequested(propertyRequest.properties, "isAllFlashSupported", "vsanSemiAutoClaimDisksData");
         if (allFlashCheckRequested) {
            isAllFlashSupported = this.isAllFlashSupported(hosts);
         }

         String[] allProperties = QueryUtil.getPropertyNames(propertyRequest.properties);
         HashMap<ManagedObjectReference, HashMap<String, Object>> hostPropsFromDs = null;
         ResultSet resultSet = null;

         try {
            hostPropsFromDs = this.getHostPropsFromDs(hosts, allProperties);
            if (allFlashCheckRequested) {
               this.appendAllFlashSupportedProperty(hostPropsFromDs, isAllFlashSupported);
            }
         } catch (Exception var10) {
            _logger.error("Failed to retrieve properties from DS. ", var10);
            resultSet = new ResultSet();
            resultSet.error = var10;
            return resultSet;
         }

         List<Callable<VsanAsyncQueryUtils.RequestResult>> requestTasks = this.getRequestTasks(allHosts, allProperties, hostPropsFromDs);
         resultSet = VsanAsyncQueryUtils.getProperties(requestTasks);
         return resultSet;
      }
   }

   private boolean isAllFlashSupported(Object[] hosts) {
      if (ArrayUtils.isEmpty(hosts)) {
         return false;
      } else {
         ManagedObjectReference firstHost = (ManagedObjectReference)hosts[0];
         Object[] var6 = hosts;
         int var5 = hosts.length;

         for(int var4 = 0; var4 < var5; ++var4) {
            Object host = var6[var4];
            if (!VsanCapabilityUtils.isAllFlashSupportedOnHost((ManagedObjectReference)host)) {
               return false;
            }
         }

         ManagedObjectReference parent = this.getHostParent(firstHost);
         return parent != null && parent.getType().equals(ClusterComputeResource.class.getSimpleName()) ? VsanCapabilityUtils.isAllFlashSupportedOnCluster(parent) : false;
      }
   }

   private ManagedObjectReference getHostParent(ManagedObjectReference host) {
      ManagedObjectReference parent = null;

      try {
         parent = (ManagedObjectReference)QueryUtil.getProperty(host, "parent", (Object)null);
      } catch (Exception var4) {
         _logger.warn("Cannot get host's parent", var4);
      }

      return parent;
   }

   private void appendAllFlashSupportedProperty(Map<ManagedObjectReference, HashMap<String, Object>> hostPropsFromDs, boolean isAllFlashSupported) {
      if (hostPropsFromDs != null && hostPropsFromDs.size() != 0) {
         Iterator var4 = hostPropsFromDs.values().iterator();

         while(var4.hasNext()) {
            HashMap<String, Object> hostProperties = (HashMap)var4.next();
            hostProperties.put("isAllFlashSupported", isAllFlashSupported);
         }

      }
   }

   private HashMap<ManagedObjectReference, HashMap<String, Object>> getHostPropsFromDs(ManagedObjectReference[] allHosts, String[] propertyNames) throws Exception {
      Set<String> propsToRetrieve = new HashSet();
      String[] var7 = propertyNames;
      int var6 = propertyNames.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         String propertyName = var7[var5];
         if (propertyName.equals("vsanDisksAndGroupsData")) {
            propsToRetrieve.add("runtime.vsanRuntimeInfo");
            propsToRetrieve.add("vsanDiskMapData");
         } else if (propertyName.equals("vsanSemiAutoClaimDisksData")) {
            propsToRetrieve.add("config.vsanHostConfig.storageInfo");
         } else if (propertyName.equals("vsanVirtualPhysicalMappingData")) {
            propsToRetrieve.add("config.vsanHostConfig.clusterInfo.nodeUuid");
            propsToRetrieve.add("name");
            propsToRetrieve.add("config.vsanHostConfig.faultDomainInfo.name");
         } else if (propertyName.equals("isHostOperational")) {
            propsToRetrieve.add("runtime.connectionState");
            propsToRetrieve.add("runtime.powerState");
            propsToRetrieve.add("runtime.inMaintenanceMode");
            propsToRetrieve.add("disabledMethod");
         }
      }

      return this.getMultipleProperties(allHosts, (String[])propsToRetrieve.toArray(new String[propsToRetrieve.size()]));
   }

   private List<Callable<VsanAsyncQueryUtils.RequestResult>> getRequestTasks(Set<ManagedObjectReference> hosts, String[] properties, HashMap<ManagedObjectReference, HashMap<String, Object>> hostProperties) {
      List<Callable<VsanAsyncQueryUtils.RequestResult>> result = new ArrayList();
      Iterator var6 = hosts.iterator();

      while(var6.hasNext()) {
         ManagedObjectReference hostRef = (ManagedObjectReference)var6.next();
         String[] var10 = properties;
         int var9 = properties.length;

         for(int var8 = 0; var8 < var9; ++var8) {
            String property = var10[var8];
            Callable<VsanAsyncQueryUtils.RequestResult> requestTask = new Callable<VsanAsyncQueryUtils.RequestResult>(hostRef, property, hostProperties) {
               private final ManagedObjectReference _target;
               private final String _property;
               private final HashMap<String, Object> _hostProp;

               {
                  this._target = var2;
                  this._property = var3;
                  this._hostProp = (HashMap)var4.get(this._target);
               }

               public VsanAsyncQueryUtils.RequestResult call() {
                  return VsanHostPropertyProviderAdapter.this.executeRequest(this._target, this._property, this._hostProp);
               }
            };
            result.add(requestTask);
         }
      }

      return result;
   }

   private VsanAsyncQueryUtils.RequestResult executeRequest(ManagedObjectReference target, String property, HashMap<String, Object> hostProperties) {
      Exception error = null;
      Object result = null;

      try {
         if (property.equals("vsanDisksAndGroupsData")) {
            result = this.getHostDisksAndGroupsData(target, hostProperties);
         } else if (property.equals("vsanDiskMapData")) {
            result = this.getDiskMappingData(target);
         } else if (property.equals("vsanSemiAutoClaimDisksData")) {
            result = this.getSemiAutoClaimDisksData(target, hostProperties);
         } else if (property.equals("vsanVirtualPhysicalMappingData")) {
            result = this.getVsanVirtualPhysicalMappingData(target, hostProperties);
         } else if (property.equals("vsanHostClusterStatus")) {
            result = this.getVsanHostClusterStatus(target);
         } else if (property.equals("vsanHostDisksForVsan")) {
            result = this.getVsanHostDiskForVsanProperty(target);
         } else if (property.equals("vsanPhysicalDiskHealthAndVersion")) {
            result = this.getVsanPhyscialDiskHealthAndVersionProperty(target, hostProperties);
         } else if (property.equals("vsanPhysicalDiskVirtualMapping")) {
            result = this.getVsanPhysicalDiskVirtualMapping(target);
         } else if (property.equals("vsanStorageAdapterDevices")) {
            result = this.getVsanHostStorageAdapterDevices(target, hostProperties);
         } else if (property.equals("isHostOperational")) {
            result = this.isHostOperational(target, hostProperties);
         } else if (property.equals("isAllFlashSupported")) {
            result = hostProperties.get("isAllFlashSupported");
         }
      } catch (Exception var7) {
         error = var7;
      }

      if (error != null) {
         _logger.error("Request for DiskMapping for " + target.toString() + " failed.", error);
      }

      return new VsanAsyncQueryUtils.RequestResult(result, error, target, property);
   }

   public VsanVirtualPhysicalMappingData getVsanVirtualPhysicalMappingData(ManagedObjectReference host, HashMap<String, Object> hostProps) throws Exception {
      if (hostProps == null) {
         return null;
      } else {
         VsanVirtualPhysicalMappingData mappingData = new VsanVirtualPhysicalMappingData();
         ArrayList<VsanHostData> hostsData = new ArrayList();
         ArrayList disksData = new ArrayList();

         try {
            Throwable var6 = null;
            Object var7 = null;

            try {
               VcConnection conn = this._vcClient.getConnection(host.getServerGuid());

               try {
                  VsanSystem vsanSystem = VsanProviderUtils.getHostVsanSystem(host, conn);
                  String nodeUuid = (String)hostProps.get("config.vsanHostConfig.clusterInfo.nodeUuid");
                  String hostName = (String)hostProps.get("name");
                  String fdName = (String)hostProps.get("config.vsanHostConfig.faultDomainInfo.name");
                  if (vsanSystem != null) {
                     VsanHostData hData = new VsanHostData();
                     hData.nodeUuid = nodeUuid;
                     hData.name = hostName;
                     hData.faultDomainName = fdName;
                     hostsData.add(hData);
                     DiskResult[] disks = this.getHostDisksForVsan(vsanSystem);
                     if (!ArrayUtils.isEmpty(disks)) {
                        DiskResult[] var18 = disks;
                        int var17 = disks.length;

                        for(int var16 = 0; var16 < var17; ++var16) {
                           DiskResult result = var18[var16];
                           disksData.add(result);
                        }
                     }
                  }
               } finally {
                  if (conn != null) {
                     conn.close();
                  }

               }
            } catch (Throwable var26) {
               if (var6 == null) {
                  var6 = var26;
               } else if (var6 != var26) {
                  var6.addSuppressed(var26);
               }

               throw var6;
            }
         } catch (Exception var27) {
            _logger.error(var27.getMessage());
            throw var27;
         }

         mappingData.hosts = (VsanHostData[])hostsData.toArray(new VsanHostData[hostsData.size()]);
         mappingData.disks = (DiskResult[])disksData.toArray(new DiskResult[disksData.size()]);
         return mappingData;
      }
   }

   private ClusterStatus getVsanHostClusterStatus(ManagedObjectReference param1) throws Exception {
      // $FF: Couldn't be decompiled
   }

   private DiskResult[] getVsanHostDiskForVsanProperty(ManagedObjectReference host) throws Exception {
      Throwable var2 = null;
      Object var3 = null;

      try {
         VcConnection vcConnection = this._vcClient.getConnection(host.getServerGuid());

         Throwable var10000;
         label173: {
            boolean var10001;
            DiskResult[] var18;
            try {
               VsanSystem vsanSystem = VsanProviderUtils.getHostVsanSystem(host, vcConnection);
               var18 = this.getHostDisksForVsan(vsanSystem);
            } catch (Throwable var16) {
               var10000 = var16;
               var10001 = false;
               break label173;
            }

            if (vcConnection != null) {
               vcConnection.close();
            }

            label162:
            try {
               return var18;
            } catch (Throwable var15) {
               var10000 = var15;
               var10001 = false;
               break label162;
            }
         }

         var2 = var10000;
         if (vcConnection != null) {
            vcConnection.close();
         }

         throw var2;
      } catch (Throwable var17) {
         if (var2 == null) {
            var2 = var17;
         } else if (var2 != var17) {
            var2.addSuppressed(var17);
         }

         throw var2;
      }
   }

   private String getVsanPhyscialDiskHealthAndVersionProperty(ManagedObjectReference host, HashMap<String, Object> hostProps) throws Exception {
      if (hostProps == null) {
         return null;
      } else {
         String result = "";
         Throwable var4 = null;
         Object var5 = null;

         try {
            VcConnection vcConnection = this._vcClient.getConnection(host.getServerGuid());

            VcConnection var10000;
            try {
               VsanInternalSystem vsanInternalSystem = VsanProviderUtils.getVsanInternalSystem(host, vcConnection);
               if (vsanInternalSystem == null) {
                  return var10000;
               }

               try {
                  Throwable var8 = null;
                  Object var9 = null;

                  try {
                     VsanProfiler.Point p = _profiler.point("vsanInternalSystem.queryPhysicalVsanDisks");

                     try {
                        result = vsanInternalSystem.queryPhysicalVsanDisks(PHYSICAL_DISK_HEALTH_AND_VERSION_PROPERTIES);
                     } finally {
                        if (p != null) {
                           p.close();
                        }

                     }
                  } catch (Throwable var32) {
                     if (var8 == null) {
                        var8 = var32;
                     } else if (var8 != var32) {
                        var8.addSuppressed(var32);
                     }

                     throw var8;
                  }
               } catch (Exception var33) {
                  _logger.error("Failed to retrieve host physical disks health: " + var33.getMessage());
               }
            } finally {
               var10000 = vcConnection;
               if (vcConnection != null) {
                  var10000 = vcConnection;
                  vcConnection.close();
               }

            }

            return result;
         } catch (Throwable var35) {
            if (var4 == null) {
               var4 = var35;
            } else if (var4 != var35) {
               var4.addSuppressed(var35);
            }

            throw var4;
         }
      }
   }

   private String getVsanPhysicalDiskVirtualMapping(ManagedObjectReference host) throws Exception {
      String result = "";
      Throwable var3 = null;
      Object var4 = null;

      try {
         VcConnection vcConnection = this._vcClient.getConnection(host.getServerGuid());

         VcConnection var10000;
         try {
            VsanInternalSystem vsanInternalSystem = VsanProviderUtils.getVsanInternalSystem(host, vcConnection);
            if (vsanInternalSystem == null) {
               return var10000;
            }

            try {
               Throwable var7 = null;
               Object var8 = null;

               try {
                  VsanProfiler.Point p = _profiler.point("vsanInternalSystem.queryPhysicalVsanDisks");

                  try {
                     result = vsanInternalSystem.queryPhysicalVsanDisks(PHYSICAL_DISK_VIRTUAL_MAPPING_PROPERTIES);
                  } finally {
                     if (p != null) {
                        p.close();
                     }

                  }
               } catch (Throwable var31) {
                  if (var7 == null) {
                     var7 = var31;
                  } else if (var7 != var31) {
                     var7.addSuppressed(var31);
                  }

                  throw var7;
               }
            } catch (Exception var32) {
               _logger.error("Failed to retrieve host physical vsan disks: " + var32.getMessage());
            }
         } finally {
            var10000 = vcConnection;
            if (vcConnection != null) {
               var10000 = vcConnection;
               vcConnection.close();
            }

         }

         return result;
      } catch (Throwable var34) {
         if (var3 == null) {
            var3 = var34;
         } else if (var3 != var34) {
            var3.addSuppressed(var34);
         }

         throw var3;
      }
   }

   private Object[] getVsanHostStorageAdapterDevices(ManagedObjectReference host, HashMap<String, Object> hostProps) throws Exception {
      return (Object[])QueryUtil.getProperty(host, "storageAdapterDevices", (Object)null);
   }

   private VsanSemiAutoClaimDisksData getSemiAutoClaimDisksData(ManagedObjectReference hostRef, HashMap<String, Object> hostProps) throws Exception {
      if (hostProps == null) {
         return null;
      } else {
         boolean isAllFlashAvailable = (Boolean)hostProps.get("isAllFlashSupported");
         DiskResult[] vsanDisks = null;
         Throwable var5 = null;
         ArrayList eligibleDisks = null;

         try {
            VcConnection connection = this._vcClient.getConnection(hostRef.getServerGuid());

            try {
               VsanSystem vsanSystem = VsanProviderUtils.getHostVsanSystem(hostRef, connection);
               vsanDisks = this.getHostDisksForVsan(vsanSystem);
            } finally {
               if (connection != null) {
                  connection.close();
               }

            }
         } catch (Throwable var17) {
            if (var5 == null) {
               var5 = var17;
            } else if (var5 != var17) {
               var5.addSuppressed(var17);
            }

            throw var5;
         }

         if (vsanDisks == null) {
            return null;
         } else {
            VsanSemiAutoClaimDisksData data = new VsanSemiAutoClaimDisksData();
            eligibleDisks = new ArrayList();
            DiskResult[] var10 = vsanDisks;
            int var9 = vsanDisks.length;

            for(int var21 = 0; var21 < var9; ++var21) {
               DiskResult diskResult = var10[var21];
               if (Utils.isDiskEligible(diskResult)) {
                  if (diskResult.disk.ssd) {
                     ++data.numNotInUseSsdDisks;
                  } else {
                     ++data.numNotInUseDataDisks;
                  }

                  VsanDiskData diskData = new VsanDiskData();
                  diskData.disk = diskResult.disk;
                  diskData.inUse = false;
                  diskData.markedAsCapacityFlash = false;
                  diskData.possibleClaimOptions = this.getPossibleClaimingOptions(isAllFlashAvailable, diskResult.disk.ssd);
                  diskData.possibleClaimOptionsIfMarkedAsOppositeType = this.getPossibleClaimingOptions(isAllFlashAvailable, !diskResult.disk.ssd);
                  eligibleDisks.add(diskData);
               }
            }

            this.populateExistingDiskGroupsInfo(data, hostProps);
            data.notInUseDisks = (VsanDiskData[])eligibleDisks.toArray(new VsanDiskData[eligibleDisks.size()]);
            data.isAllFlashAvailable = isAllFlashAvailable;
            VsanAllFlashClaimOptionRecommender allFlashRecommender = new VsanAllFlashClaimOptionRecommender(data, (Map)null);
            VsanHybridClaimOptionRecommender hybridRecommender = new VsanHybridClaimOptionRecommender(data);
            allFlashRecommender.recommend();
            hybridRecommender.recommend();
            return data;
         }
      }
   }

   private void populateExistingDiskGroupsInfo(VsanSemiAutoClaimDisksData data, HashMap<String, Object> hostProps) {
      StorageInfo storageInfo = (StorageInfo)hostProps.get("config.vsanHostConfig.storageInfo");
      if (storageInfo != null) {
         DiskMapping[] diskMapping = storageInfo.diskMapping;
         if (!ArrayUtils.isEmpty(diskMapping)) {
            long totalCapacity = 0L;
            long totalCache = 0L;
            DiskMapping[] var12 = diskMapping;
            int var11 = diskMapping.length;

            for(int var10 = 0; var10 < var11; ++var10) {
               DiskMapping mapping = var12[var10];
               totalCache += BaseUtils.lbaToBytes(mapping.ssd.capacity);
               ScsiDisk[] storageDisks = mapping.nonSsd;
               if (!ArrayUtils.isEmpty(storageDisks)) {
                  ScsiDisk firstDisk = storageDisks[0];
                  if (firstDisk.ssd) {
                     data.allFlashDiskGroupExist = true;
                     ++data.numAllFlashGroups;
                     data.numAllFlashCapacityDisks += storageDisks.length;
                  } else {
                     data.hybridDiskGroupExist = true;
                     ++data.numHybridGroups;
                     data.numHybridCapacityDisks += storageDisks.length;
                  }

                  totalCapacity += BaseUtils.lbaToBytes(firstDisk.capacity);

                  for(int i = 1; i < storageDisks.length; ++i) {
                     totalCapacity += BaseUtils.lbaToBytes(storageDisks[i].capacity);
                  }
               }
            }

            data.claimedCapacity = totalCapacity;
            data.claimedCache = totalCache;
         }
      }
   }

   private ClaimOption[] getPossibleClaimingOptions(boolean isAllFlashAvailable, boolean isFlashDisk) {
      List<ClaimOption> claimOptions = new ArrayList();
      if (isFlashDisk) {
         claimOptions.add(ClaimOption.ClaimForCache);
      }

      if (!isFlashDisk || isAllFlashAvailable) {
         claimOptions.add(ClaimOption.ClaimForStorage);
      }

      claimOptions.add(ClaimOption.DoNotClaim);
      return (ClaimOption[])claimOptions.toArray(new ClaimOption[claimOptions.size()]);
   }

   private VsanDiskAndGroupData getHostDisksAndGroupsData(ManagedObjectReference hostRef, HashMap<String, Object> hostProps) throws Exception {
      if (hostProps == null) {
         return null;
      } else {
         VsanRuntimeInfo vsanRuntimeInfo = (VsanRuntimeInfo)hostProps.get("runtime.vsanRuntimeInfo");
         HashMap<String, ArrayList<String>> disksIssues = getDisksIssuesOnHost(vsanRuntimeInfo);
         ArrayList<VsanDiskData> vsanDisksData = new ArrayList();
         ArrayList<VsanDiskData> disksNotInUseData = new ArrayList();
         ArrayList<VsanDiskData> ineligibleDisksData = new ArrayList();
         HashMap<String, VsanDiskData> connectedDisksData = new HashMap();
         HashMap<String, Boolean> isDiskInDiskGroupMap = new HashMap();
         Throwable var10 = null;
         DiskMapInfoEx[] diskMappingData = null;

         try {
            VcConnection conn = this._vcClient.getConnection(hostRef.getServerGuid());

            try {
               VsanSystem vsanSystem = VsanProviderUtils.getHostVsanSystem(hostRef, conn);
               this.populateDisksData(vsanSystem, connectedDisksData, disksIssues);
            } finally {
               if (conn != null) {
                  conn.close();
               }

            }
         } catch (Throwable var20) {
            if (var10 == null) {
               var10 = var20;
            } else if (var10 != var20) {
               var10.addSuppressed(var20);
            }

            throw var10;
         }

         if (connectedDisksData.size() == 0) {
            return null;
         } else {
            VsanDiskAndGroupData disksGroupsData = new VsanDiskAndGroupData();
            disksGroupsData.connectedDisks = (VsanDiskData[])connectedDisksData.values().toArray(new VsanDiskData[connectedDisksData.size()]);
            diskMappingData = (DiskMapInfoEx[])hostProps.get("vsanDiskMapData");
            disksGroupsData.vsanGroups = this.retrieveDiskGroups(connectedDisksData, isDiskInDiskGroupMap, diskMappingData);
            Iterator var23 = connectedDisksData.values().iterator();

            while(var23.hasNext()) {
               VsanDiskData diskData = (VsanDiskData)var23.next();
               if (diskData != null && diskData.disk != null) {
                  String diskId = diskData.disk.uuid;
                  if (!isDiskInDiskGroupMap.containsKey(diskId)) {
                     if (diskData.ineligible) {
                        ineligibleDisksData.add(diskData);
                     } else if (diskData.inUse) {
                        vsanDisksData.add(diskData);
                     } else {
                        disksNotInUseData.add(diskData);
                     }
                  }
               }
            }

            if (ineligibleDisksData.size() > 0) {
               disksGroupsData.ineligibleDisks = (VsanDiskData[])ineligibleDisksData.toArray(new VsanDiskData[ineligibleDisksData.size()]);
            }

            if (disksNotInUseData.size() > 0) {
               disksGroupsData.disksNotInUse = (VsanDiskData[])disksNotInUseData.toArray(new VsanDiskData[disksNotInUseData.size()]);
            }

            if (vsanDisksData.size() > 0) {
               disksGroupsData.vsanDisks = (VsanDiskData[])vsanDisksData.toArray(new VsanDiskData[vsanDisksData.size()]);
            }

            return disksGroupsData;
         }
      }
   }

   private DiskMapInfoEx[] getDiskMappingData(ManagedObjectReference hostRef) {
      DiskMapInfoEx[] result = new DiskMapInfoEx[0];
      VsanVcDiskManagementSystem diskMgmtSystem = VsanProviderUtils.getVcDiskManagementSystem(hostRef);

      try {
         Throwable var4 = null;
         Object var5 = null;

         try {
            VsanProfiler.Point p = _profiler.point("diskMgmtSystem.queryDiskMappings");

            try {
               result = diskMgmtSystem.queryDiskMappings(hostRef);
            } finally {
               if (p != null) {
                  p.close();
               }

            }
         } catch (Throwable var14) {
            if (var4 == null) {
               var4 = var14;
            } else if (var4 != var14) {
               var4.addSuppressed(var14);
            }

            throw var4;
         }
      } catch (Exception var15) {
         _logger.warn("Cannot query host's disk mappings from VsanVcDiskManagementSystem", var15);
         _logger.info("Trying to get disk mappings from host's storageInfo property");
         result = getDiskMapingFallback(hostRef);
      }

      return result;
   }

   public static DiskMapInfoEx[] getDiskMapingFallback(ManagedObjectReference hostRef) {
      StorageInfo storageInfo = null;

      try {
         storageInfo = (StorageInfo)QueryUtil.getProperty(hostRef, "config.vsanHostConfig.storageInfo", (Object)null);
      } catch (Exception var8) {
         _logger.error("Cannot get host's disk mappings", var8);
      }

      List<DiskMapInfoEx> resultList = new ArrayList();
      if (storageInfo != null && storageInfo.diskMapping != null) {
         DiskMapping[] var6;
         int var5 = (var6 = storageInfo.diskMapping).length;

         for(int var4 = 0; var4 < var5; ++var4) {
            DiskMapping mapping = var6[var4];
            DiskMapInfoEx info = new DiskMapInfoEx();
            info.mapping = mapping;
            info.isMounted = isDiskGroupMounted(mapping, storageInfo);
            info.isAllFlash = false;
            info.isDataEfficiency = false;
            info.encryptionInfo = null;
            info.unlockedEncrypted = false;
            resultList.add(info);
         }
      }

      return (DiskMapInfoEx[])resultList.toArray(new DiskMapInfoEx[0]);
   }

   private static HashMap<String, ArrayList<String>> getDisksIssuesOnHost(VsanRuntimeInfo runtimeInfo) {
      if (runtimeInfo != null && !ArrayUtils.isEmpty(runtimeInfo.diskIssues)) {
         HashMap<String, ArrayList<String>> issuesMap = new HashMap();
         DiskIssue[] var5;
         int var4 = (var5 = runtimeInfo.diskIssues).length;

         for(int var3 = 0; var3 < var4; ++var3) {
            DiskIssue diskIssue = var5[var3];
            ArrayList<String> issues = null;
            String diskId = diskIssue.diskId;
            if (issuesMap.containsKey(diskId)) {
               issues = (ArrayList)issuesMap.get(diskId);
            } else {
               issues = new ArrayList();
               issuesMap.put(diskId, issues);
            }

            issues.add(diskIssue.issue);
         }

         return issuesMap;
      } else {
         return null;
      }
   }

   private VsanDiskGroupData[] retrieveDiskGroups(HashMap<String, VsanDiskData> connectedDisksData, HashMap<String, Boolean> isDiskInDiskGroup, DiskMapInfoEx[] diskMappingData) {
      if (connectedDisksData != null && connectedDisksData.size() != 0) {
         if (ArrayUtils.isEmpty(diskMappingData)) {
            return null;
         } else {
            VsanDiskGroupData[] groups = new VsanDiskGroupData[diskMappingData.length];
            int i = 0;
            DiskMapInfoEx[] var9 = diskMappingData;
            int var8 = diskMappingData.length;

            for(int var7 = 0; var7 < var8; ++var7) {
               DiskMapInfoEx diskMapInfoEx = var9[var7];
               DiskMapping mapping = diskMapInfoEx.mapping;
               String ssdId = mapping.ssd.uuid;
               VsanDiskGroupData groupData = new VsanDiskGroupData();
               groupData.ssd = (VsanDiskData)connectedDisksData.get(ssdId);
               groupData.ssd.diskGroupUuid = ssdId;
               groupData.ssd.isCacheDisk = true;
               groupData.mounted = diskMapInfoEx.isMounted;
               groupData.encrypted = diskMapInfoEx.encryptionInfo != null && diskMapInfoEx.encryptionInfo.encryptionEnabled;
               groupData.unlocked = diskMapInfoEx.unlockedEncrypted != null && diskMapInfoEx.unlockedEncrypted;
               groupData.isAllFlash = diskMapInfoEx.isAllFlash;
               isDiskInDiskGroup.put(ssdId, true);
               ArrayList<VsanDiskData> disks = new ArrayList();
               ScsiDisk[] var17;
               int var16 = (var17 = mapping.nonSsd).length;

               for(int var15 = 0; var15 < var16; ++var15) {
                  ScsiDisk disk = var17[var15];
                  String diskId = disk.uuid;
                  VsanDiskData diskData = (VsanDiskData)connectedDisksData.get(diskId);
                  diskData.diskGroupUuid = ssdId;
                  diskData.isCacheDisk = false;
                  if (diskData != null) {
                     disks.add(diskData);
                  }

                  isDiskInDiskGroup.put(diskId, true);
               }

               groupData.disks = (VsanDiskData[])disks.toArray(new VsanDiskData[disks.size()]);
               groups[i++] = groupData;
            }

            return groups;
         }
      } else {
         return null;
      }
   }

   private static boolean isDiskGroupMounted(DiskMapping mapping, StorageInfo storageInfo) {
      if (mapping != null && mapping.ssd != null && storageInfo.diskMapInfo != null) {
         String ssdId = mapping.ssd.uuid;
         if (ssdId == null) {
            return true;
         } else {
            DiskMapInfo[] var6;
            int var5 = (var6 = storageInfo.diskMapInfo).length;

            for(int var4 = 0; var4 < var5; ++var4) {
               DiskMapInfo diskMapInfo = var6[var4];
               if (diskMapInfo.mapping != null && diskMapInfo.mapping.ssd != null && ssdId.equals(diskMapInfo.mapping.ssd.uuid)) {
                  return diskMapInfo.mounted;
               }
            }

            return true;
         }
      } else {
         return true;
      }
   }

   private void populateDisksData(VsanSystem vsanSystem, HashMap<String, VsanDiskData> connectedDisks, HashMap<String, ArrayList<String>> diskIssues) {
      DiskResult[] results = this.getHostDisksForVsan(vsanSystem);
      if (results != null) {
         DiskResult[] var8 = results;
         int var7 = results.length;

         for(int var6 = 0; var6 < var7; ++var6) {
            DiskResult result = var8[var6];
            String resultState = result.state;
            State diskState = (State)Enum.valueOf(State.class, resultState);
            VsanDiskData diskData = new VsanDiskData();
            if (result.error != null) {
               diskData.stateReason = result.error.getLocalizedMessage();
            }

            diskData.disk = result.disk;
            diskData.vsanUuid = result.vsanUuid;
            if (diskState == State.ineligible) {
               diskData.inUse = false;
               diskData.ineligible = true;
            } else if (diskState == State.eligible) {
               diskData.inUse = false;
            } else if (diskState == State.inUse) {
               diskData.inUse = true;
            }

            diskData.diskLocality = this.getDiskLocality(diskData.disk);
            String diskId = diskData.disk.uuid;
            if (diskIssues != null && diskIssues.containsKey(diskId)) {
               ArrayList<String> issues = (ArrayList)diskIssues.get(diskId);
               if (!CollectionUtils.isEmpty(issues)) {
                  diskData.issues = (String[])issues.toArray(new String[issues.size()]);
               }
            }

            connectedDisks.put(diskId, diskData);
         }

      }
   }

   private DiskLocalityType getDiskLocality(ScsiDisk disk) {
      DiskLocalityType result = DiskLocalityType.Unknown;
      if (disk.localDisk != null) {
         result = disk.localDisk ? DiskLocalityType.Local : DiskLocalityType.Remote;
      }

      return result;
   }

   private DiskResult[] getHostDisksForVsan(VsanSystem vsanSystem) {
      if (vsanSystem == null) {
         return null;
      } else {
         DiskResult[] disks = null;

         try {
            Throwable var3 = null;
            Object var4 = null;

            try {
               VsanProfiler.Point point = _profiler.point("vsanSystem.queryDisksForVsan");

               try {
                  disks = vsanSystem.queryDisksForVsan((String[])null);
               } finally {
                  if (point != null) {
                     point.close();
                  }

               }
            } catch (Throwable var13) {
               if (var3 == null) {
                  var3 = var13;
               } else if (var3 != var13) {
                  var3.addSuppressed(var13);
               }

               throw var3;
            }
         } catch (Exception var14) {
            _logger.error("Failed to get host disks for VSAN from " + vsanSystem._getRef().toString(), var14);
         }

         return disks;
      }
   }

   private Set<ManagedObjectReference> getHosts(Object[] hosts) {
      if (hosts != null && hosts.length != 0) {
         Set<ManagedObjectReference> result = new HashSet();
         Object[] var6 = hosts;
         int var5 = hosts.length;

         for(int var4 = 0; var4 < var5; ++var4) {
            Object o = var6[var4];
            if (o instanceof ManagedObjectReference) {
               ManagedObjectReference host = (ManagedObjectReference)o;
               result.add(host);
            }
         }

         return result;
      } else {
         return Collections.emptySet();
      }
   }

   private boolean isValidRequest(PropertyRequestSpec propertyRequest) {
      if (propertyRequest == null) {
         return false;
      } else if (!ArrayUtils.isEmpty(propertyRequest.objects) && !ArrayUtils.isEmpty(propertyRequest.properties)) {
         return true;
      } else {
         _logger.error("Property provider adapter got a null or empty list of properties or objects");
         return false;
      }
   }

   private HashMap<ManagedObjectReference, HashMap<String, Object>> getMultipleProperties(ManagedObjectReference[] hosts, String[] properties) throws Exception {
      HashMap<ManagedObjectReference, HashMap<String, Object>> result = new HashMap();
      if (properties != null && properties.length != 0 && hosts != null && hosts.length != 0) {
         PropertyValue[] propValues = QueryUtil.getProperties(hosts, properties).getPropertyValues();
         if (propValues != null) {
            PropertyValue[] var8 = propValues;
            int var7 = propValues.length;

            for(int var6 = 0; var6 < var7; ++var6) {
               PropertyValue propValue = var8[var6];
               ManagedObjectReference hostRef = (ManagedObjectReference)propValue.resourceObject;
               if (!result.containsKey(hostRef)) {
                  result.put(hostRef, new HashMap());
               }

               HashMap<String, Object> propMap = (HashMap)result.get(hostRef);
               propMap.put(propValue.propertyName, propValue.value);
            }
         }

         return result;
      } else {
         return result;
      }
   }

   private boolean isHostOperational(ManagedObjectReference host, HashMap<String, Object> hostProps) {
      ConnectionState connectionState = (ConnectionState)hostProps.get("runtime.connectionState");
      PowerState powerState = (PowerState)hostProps.get("runtime.powerState");
      boolean isInMaintenanceMode = (Boolean)hostProps.get("runtime.inMaintenanceMode");
      List<String> disabledMethods = Arrays.asList((String[])hostProps.get("disabledMethod"));
      boolean isHostConnected = connectionState.equals(ConnectionState.connected) && PowerState.poweredOn.equals(powerState) && !isInMaintenanceMode && !disabledMethods.containsAll(Arrays.asList("EnterMaintenanceMode_Task", "ExitMaintenanceMode_Task"));
      return isHostConnected;
   }
}
