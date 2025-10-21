package com.vmware.vsan.client.services.cns;

import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.pbm.capability.CapabilityInstance;
import com.vmware.vim.binding.pbm.capability.CapabilityMetadata;
import com.vmware.vim.binding.pbm.capability.ConstraintInstance;
import com.vmware.vim.binding.pbm.capability.PropertyInstance;
import com.vmware.vim.binding.pbm.capability.PropertyMetadata;
import com.vmware.vim.binding.pbm.capability.provider.CapabilityObjectMetadataPerCategory;
import com.vmware.vim.binding.pbm.capability.provider.CapabilityObjectSchema;
import com.vmware.vim.binding.pbm.capability.types.DiscreteSet;
import com.vmware.vim.binding.pbm.compliance.ComplianceResult;
import com.vmware.vim.binding.pbm.compliance.PolicyStatus;
import com.vmware.vim.binding.pbm.profile.ReconfigOutcome;
import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.Datacenter;
import com.vmware.vim.binding.vim.Datastore;
import com.vmware.vim.binding.vim.HostSystem;
import com.vmware.vim.binding.vim.KeyValue;
import com.vmware.vim.binding.vim.VirtualMachine;
import com.vmware.vim.binding.vim.Datastore.HostMount;
import com.vmware.vim.binding.vim.vslm.ID;
import com.vmware.vim.binding.vim.vslm.VStorageObject;
import com.vmware.vim.binding.vim.vslm.BaseConfigInfo.BackingInfo;
import com.vmware.vim.binding.vim.vslm.BaseConfigInfo.DiskFileBackingInfo;
import com.vmware.vim.binding.vim.vslm.vcenter.RetrieveVStorageObjSpec;
import com.vmware.vim.binding.vim.vslm.vcenter.VStorageObjectAssociations;
import com.vmware.vim.binding.vim.vslm.vcenter.VStorageObjectManager;
import com.vmware.vim.binding.vim.vslm.vcenter.VStorageObjectAssociations.VmDiskAssociations;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cns.ContainerCluster;
import com.vmware.vim.vsan.binding.vim.cns.Cursor;
import com.vmware.vim.vsan.binding.vim.cns.EntityMetadata;
import com.vmware.vim.vsan.binding.vim.cns.KubernetesEntityMetadata;
import com.vmware.vim.vsan.binding.vim.cns.QueryFilter;
import com.vmware.vim.vsan.binding.vim.cns.QueryResult;
import com.vmware.vim.vsan.binding.vim.cns.VolumeId;
import com.vmware.vim.vsan.binding.vim.cns.VolumeManager;
import com.vmware.vise.data.Constraint;
import com.vmware.vise.data.query.Comparator;
import com.vmware.vise.data.query.Conjoiner;
import com.vmware.vise.data.query.ObjectIdentityConstraint;
import com.vmware.vise.data.query.PropertyConstraint;
import com.vmware.vise.data.query.QuerySpec;
import com.vmware.vise.data.query.RelationalConstraint;
import com.vmware.vise.data.query.ResultSet;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.cns.model.CnsDatastoreAccessibilityStatus;
import com.vmware.vsan.client.services.cns.model.CnsHostData;
import com.vmware.vsan.client.services.cns.model.CnsLabel;
import com.vmware.vsan.client.services.cns.model.QueryLabelResult;
import com.vmware.vsan.client.services.cns.model.Volume;
import com.vmware.vsan.client.services.cns.model.VolumeComplianceFailure;
import com.vmware.vsan.client.services.cns.model.VolumeDatastoreData;
import com.vmware.vsan.client.services.cns.model.VolumeFilterResult;
import com.vmware.vsan.client.services.cns.model.VolumeFilterSpec;
import com.vmware.vsan.client.services.cns.model.VolumeVirtualObject;
import com.vmware.vsan.client.services.common.data.BasicVmData;
import com.vmware.vsan.client.services.common.data.StorageCompliance;
import com.vmware.vsan.client.services.virtualobjects.data.VirtualObjectsFilter;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsan.client.util.retriever.VsanAsyncDataRetriever;
import com.vmware.vsan.client.util.retriever.VsanDataRetrieverFactory;
import com.vmware.vsphere.client.vsan.util.DataServiceResponse;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsan.util.Utils;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.PbmClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VsanClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vsan.VsanConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CnsService {
   private static final String PERSISTENT_VOLUME_CLAIM = "PERSISTENT_VOLUME_CLAIM";
   private static final String POD = "POD";
   private static final String PERSISTENT_VOLUME = "PERSISTENT_VOLUME";
   private static final String COMPLIANCE_FAILURE_TAG_DELIMITER = ",";
   private static final long QUERY_LABELS_MAX_RESULTS = 10L;
   private static final Logger logger = LoggerFactory.getLogger(CnsService.class);
   @Autowired
   private VmodlHelper vmodlHelper;
   @Autowired
   private VsanClient vsanClient;
   @Autowired
   private VcClient vcClient;
   @Autowired
   private VsanDataRetrieverFactory dataRetrieverFactory;
   @Autowired
   private PbmClient pbmClient;

   @TsService
   public VolumeFilterResult getVolumes(ManagedObjectReference contextObjectRef, VolumeFilterSpec filterSpec) throws Exception {
      VolumeFilterResult result = new VolumeFilterResult();
      Map<ManagedObjectReference, CnsService.DatastoreMetadata> allDatastoresMetadata = this.queryDatastores(contextObjectRef, filterSpec.datastore);
      if (MapUtils.isEmpty(allDatastoresMetadata)) {
         result.volumes = new Volume[0];
         return result;
      } else {
         QueryFilter filter = this.createQueryFilter(filterSpec, allDatastoresMetadata);
         Throwable var6 = null;
         Object var7 = null;

         try {
            VsanConnection connection = this.vsanClient.getConnection(contextObjectRef.getServerGuid());

            VsanConnection var10000;
            try {
               VolumeManager volumeManager = connection.getCnsVolumeManager();
               Throwable var11 = null;
               Object var12 = null;

               QueryResult queryResult;
               try {
                  Measure measure = new Measure("VolumeManager.query");

                  try {
                     queryResult = volumeManager.query(filter);
                  } finally {
                     if (measure != null) {
                        measure.close();
                     }

                  }
               } catch (Throwable var31) {
                  if (var11 == null) {
                     var11 = var31;
                  } else if (var11 != var31) {
                     var11.addSuppressed(var31);
                  }

                  throw var11;
               }

               result.total = queryResult.cursor.totalRecords;
               if (!ArrayUtils.isEmpty(queryResult.volumes)) {
                  List<Volume> volumes = this.createVolumes(queryResult, allDatastoresMetadata);
                  result.volumes = (Volume[])volumes.toArray(new Volume[0]);
                  return result;
               }

               result.volumes = new Volume[0];
            } finally {
               var10000 = connection;
               if (connection != null) {
                  var10000 = connection;
                  connection.close();
               }

            }

            return var10000;
         } catch (Throwable var33) {
            if (var6 == null) {
               var6 = var33;
            } else if (var6 != var33) {
               var6.addSuppressed(var33);
            }

            throw var6;
         }
      }
   }

   private QueryFilter createQueryFilter(VolumeFilterSpec filterSpec, Map<ManagedObjectReference, CnsService.DatastoreMetadata> allDatastoresMetadata) {
      QueryFilter filter = new QueryFilter();
      if (StringUtils.isNotEmpty(filterSpec.name)) {
         filter.names = new String[]{filterSpec.name};
      }

      if (StringUtils.isNotEmpty(filterSpec.containerCluster)) {
         filter.containerClusterIds = new String[]{filterSpec.containerCluster};
      }

      if (!ArrayUtils.isEmpty(filterSpec.labels)) {
         filter.labels = CnsLabel.toKeyValue(filterSpec.labels);
      }

      if (StringUtils.isNotEmpty(filterSpec.id)) {
         VolumeId volumeId = new VolumeId();
         volumeId.id = filterSpec.id;
         filter.volumeIds = new VolumeId[]{volumeId};
      }

      if (StringUtils.isNotEmpty(filterSpec.complianceStatus)) {
         filter.complianceStatus = filterSpec.complianceStatus;
      }

      if (StringUtils.isNotEmpty(filterSpec.accessibilityStatus)) {
         filter.datastoreAccessibilityStatus = filterSpec.accessibilityStatus;
      }

      filter.storagePolicyId = filterSpec.storagePolicy;
      filter.datastores = (ManagedObjectReference[])allDatastoresMetadata.keySet().toArray(new ManagedObjectReference[0]);
      filter.cursor = new Cursor();
      filter.cursor.limit = filterSpec.limit;
      filter.cursor.offset = filterSpec.offset;
      return filter;
   }

   private Map<ManagedObjectReference, CnsService.DatastoreMetadata> queryDatastores(ManagedObjectReference contextObjectRef, String filter) throws Exception {
      if (this.vmodlHelper.isVcRootFolder(contextObjectRef)) {
         ObjectIdentityConstraint objectConstraint = QueryUtil.createObjectIdentityConstraint(contextObjectRef);
         RelationalConstraint dcConstraint = QueryUtil.createRelationalConstraint("childEntity", objectConstraint, true, Datacenter.class.getSimpleName());
         Constraint dsConstraint = this.applyFilter(dcConstraint, filter);
         return this.queryDatastores(dsConstraint);
      } else if (!this.vmodlHelper.isOfType(contextObjectRef, Datacenter.class) && !this.vmodlHelper.isOfType(contextObjectRef, ClusterComputeResource.class)) {
         if (this.vmodlHelper.isOfType(contextObjectRef, Datastore.class)) {
            return this.queryDatastores(QueryUtil.createObjectIdentityConstraint(contextObjectRef));
         } else {
            logger.error("Datastores query should be invoked only for VC, DC, Cluster or Datastore.Currently it is invoked for " + contextObjectRef.getType());
            throw new VsanUiLocalizableException("vsan.common.error.dataExtraction");
         }
      } else {
         Constraint dsConstraint = this.applyFilter(QueryUtil.createObjectIdentityConstraint(contextObjectRef), filter);
         return this.queryDatastores(dsConstraint);
      }
   }

   private Constraint applyFilter(Constraint dataCenterConstraint, String filter) {
      RelationalConstraint dsConstraint = QueryUtil.createRelationalConstraint("datastore", dataCenterConstraint, true, Datastore.class.getSimpleName());
      Constraint compositeConstraint = dsConstraint;
      if (StringUtils.isNotEmpty(filter)) {
         PropertyConstraint propertyConstraint = QueryUtil.createPropertyConstraint(Datastore.class.getSimpleName(), "name", Comparator.CONTAINS, filter);
         compositeConstraint = QueryUtil.combineIntoSingleConstraint(new Constraint[]{dsConstraint, propertyConstraint}, Conjoiner.AND);
      }

      return (Constraint)compositeConstraint;
   }

   private Map<ManagedObjectReference, CnsService.DatastoreMetadata> queryDatastores(Constraint datastoresConstraint) throws Exception {
      Map<ManagedObjectReference, CnsService.DatastoreMetadata> result = new HashMap();
      String[] properties = new String[]{"name", "summary.type", "summary.url"};
      QuerySpec query = QueryUtil.buildQuerySpec(datastoresConstraint, properties);
      Throwable var6 = null;
      Object resourceObject = null;

      ResultSet resultSet;
      try {
         Measure measure = new Measure("Query datastores");

         try {
            resultSet = QueryUtil.getData(query);
         } finally {
            if (measure != null) {
               measure.close();
            }

         }
      } catch (Throwable var15) {
         if (var6 == null) {
            var6 = var15;
         } else if (var6 != var15) {
            var6.addSuppressed(var15);
         }

         throw var6;
      }

      DataServiceResponse response = QueryUtil.getDataServiceResponse(resultSet, properties);

      CnsService.DatastoreMetadata metadata;
      for(Iterator var17 = response.getResourceObjects().iterator(); var17.hasNext(); metadata.datastoreUrl = (String)response.getProperty(resourceObject, "summary.url")) {
         resourceObject = var17.next();
         metadata = new CnsService.DatastoreMetadata();
         result.put((ManagedObjectReference)resourceObject, metadata);
         metadata.name = (String)response.getProperty(resourceObject, "name");
         metadata.type = (String)response.getProperty(resourceObject, "summary.type");
      }

      return result;
   }

   private List<Volume> createVolumes(QueryResult queryResult, Map<ManagedObjectReference, CnsService.DatastoreMetadata> allDatastoresMetadata) {
      Multimap<String, ManagedObjectReference> dsUrlToMoRefMap = this.bindDatastoreUrlToDatastoreRefs(allDatastoresMetadata);
      List<Volume> volumes = new ArrayList();
      com.vmware.vim.vsan.binding.vim.cns.Volume[] var8;
      int var7 = (var8 = queryResult.volumes).length;

      for(int var6 = 0; var6 < var7; ++var6) {
         com.vmware.vim.vsan.binding.vim.cns.Volume cnsVolume = var8[var6];
         Collection<ManagedObjectReference> volumeDatastoreRefs = dsUrlToMoRefMap.get(cnsVolume.datastoreUrl);
         if (CollectionUtils.isEmpty(volumeDatastoreRefs)) {
            logger.warn("Cannot map data store moRef to the cns volume. Data store MoRef doesn't exists for " + cnsVolume.datastoreUrl + " !");
         } else {
            ArrayList<VolumeDatastoreData> datastoreDataList = new ArrayList(volumeDatastoreRefs.size());
            String datastoreType = null;
            Iterator var13 = volumeDatastoreRefs.iterator();

            while(var13.hasNext()) {
               ManagedObjectReference datastoreRef = (ManagedObjectReference)var13.next();
               CnsService.DatastoreMetadata dsMetadata = (CnsService.DatastoreMetadata)allDatastoresMetadata.get(datastoreRef);
               if (StringUtils.isEmpty(datastoreType)) {
                  datastoreType = dsMetadata.type;
               }

               VolumeDatastoreData datastoreData = new VolumeDatastoreData();
               datastoreData.name = dsMetadata.name;
               datastoreData.reference = datastoreRef;
               datastoreDataList.add(datastoreData);
            }

            Volume volume = this.createVolume(datastoreDataList, datastoreType, cnsVolume);
            volumes.add(volume);
         }
      }

      return volumes;
   }

   private Multimap<String, ManagedObjectReference> bindDatastoreUrlToDatastoreRefs(Map<ManagedObjectReference, CnsService.DatastoreMetadata> datastoresMetadata) {
      Multimap<String, ManagedObjectReference> result = ArrayListMultimap.create();
      Iterator var4 = datastoresMetadata.entrySet().iterator();

      while(var4.hasNext()) {
         Entry<ManagedObjectReference, CnsService.DatastoreMetadata> dsEntry = (Entry)var4.next();
         result.put(((CnsService.DatastoreMetadata)dsEntry.getValue()).datastoreUrl, (ManagedObjectReference)dsEntry.getKey());
      }

      return result;
   }

   private Volume createVolume(ArrayList<VolumeDatastoreData> dsData, String dsType, com.vmware.vim.vsan.binding.vim.cns.Volume cnsVolume) {
      Volume volume = new Volume();
      volume.id = cnsVolume.volumeId.id;
      volume.name = cnsVolume.name;
      volume.accessibility = CnsDatastoreAccessibilityStatus.fromName(cnsVolume.datastoreAccessibilityStatus);
      if (cnsVolume.metadata != null) {
         if (ArrayUtils.isNotEmpty(cnsVolume.metadata.entityMetadata)) {
            List<KeyValue> labels = new ArrayList();
            EntityMetadata[] var9;
            int var8 = (var9 = cnsVolume.metadata.entityMetadata).length;

            for(int var7 = 0; var7 < var8; ++var7) {
               EntityMetadata metadata = var9[var7];
               if (metadata instanceof KubernetesEntityMetadata) {
                  KubernetesEntityMetadata kMetadata = (KubernetesEntityMetadata)metadata;
                  String var11;
                  switch((var11 = kMetadata.entityType).hashCode()) {
                  case -1464742049:
                     if (var11.equals("PERSISTENT_VOLUME_CLAIM")) {
                        volume.persistentVolumeClaimMetadata.add(kMetadata);
                     }
                     break;
                  case -1261660734:
                     if (var11.equals("PERSISTENT_VOLUME")) {
                        volume.persistentVolumeMetadata.add(kMetadata);
                     }
                     break;
                  case 79397:
                     if (var11.equals("POD")) {
                        volume.podNames.add(kMetadata.entityName);
                     }
                  }
               }

               if (!ArrayUtils.isEmpty(metadata.labels)) {
                  labels.addAll(Arrays.asList(metadata.labels));
               }
            }

            if (labels.size() > 0) {
               volume.labels = CnsLabel.fromKeyValue((KeyValue[])labels.toArray(new KeyValue[0]));
            }
         }

         ContainerCluster containerCluster = cnsVolume.metadata.containerCluster;
         if (containerCluster != null) {
            volume.containerCluster = containerCluster.clusterId;
         }
      }

      volume.type = cnsVolume.volumeType;
      volume.datastoreData = dsData;
      volume.storagePolicyId = cnsVolume.storagePolicyId;
      if (cnsVolume.backingObjectDetails != null) {
         volume.capacity = cnsVolume.backingObjectDetails.capacityInMb * 1024L * 1024L;
      } else {
         volume.capacity = 0L;
      }

      volume.isVsanDatastore = "vsan".equals(dsType);
      volume.compliance = StorageCompliance.fromName(cnsVolume.complianceStatus);
      return volume;
   }

   @TsService
   public List<BasicVmData> getVolumeVmsData(ManagedObjectReference param1, String param2) throws VsanUiLocalizableException {
      // $FF: Couldn't be decompiled
   }

   private List<BasicVmData> getVmData(ManagedObjectReference moRef, List<VmDiskAssociations> vmDiskAssociations) throws Exception {
      List<BasicVmData> result = new ArrayList();
      List<ManagedObjectReference> vmRefs = new ArrayList();
      Iterator var6 = vmDiskAssociations.iterator();

      while(var6.hasNext()) {
         VmDiskAssociations vmDiskAssociation = (VmDiskAssociations)var6.next();
         ManagedObjectReference vmRef = new ManagedObjectReference(VirtualMachine.class.getSimpleName(), vmDiskAssociation.vmId, moRef.getServerGuid());
         vmRefs.add(vmRef);
      }

      String[] vmProperties = new String[]{"name", "primaryIconId"};
      DataServiceResponse response = QueryUtil.getProperties((ManagedObjectReference[])vmRefs.toArray(new ManagedObjectReference[0]), vmProperties);
      Iterator var8 = response.getResourceObjects().iterator();

      while(var8.hasNext()) {
         Object resourceObject = var8.next();
         BasicVmData vmData = new BasicVmData((ManagedObjectReference)resourceObject);
         vmData.name = (String)response.getProperty(resourceObject, "name");
         vmData.primaryIconId = (String)response.getProperty(resourceObject, "primaryIconId");
         result.add(vmData);
      }

      Collections.sort(result, BasicVmData.COMPARATOR);
      return result;
   }

   @TsService
   public VolumeVirtualObject getVolumeVirtualObject(ManagedObjectReference datastoreRef, String volumeID) throws Exception {
      VolumeVirtualObject result = new VolumeVirtualObject();
      result.virtualObjectsFilter = VirtualObjectsFilter.VOLUMES;
      Throwable var4 = null;
      Object var5 = null;

      try {
         VcConnection connection = this.vcClient.getConnection(datastoreRef.getServerGuid());

         try {
            Datastore datastore = (Datastore)connection.createStub(Datastore.class, datastoreRef);
            HostMount[] hosts = datastore.getHost();
            if (ArrayUtils.isEmpty(hosts)) {
               logger.error("Datastore " + datastoreRef + " does not have any attached hosts.");
               throw new VsanUiLocalizableException("vsan.common.generic.error");
            }

            HostSystem host = (HostSystem)connection.createStub(HostSystem.class, hosts[0].key);
            result.cluster = host.getParent();
            VmodlHelper.assignServerGuid(result.cluster, datastoreRef.getServerGuid());
            VStorageObjectManager vStorageObjectManager = connection.getVStorageObjectManager();
            VStorageObject vStorageObject = vStorageObjectManager.retrieveVStorageObject(new ID(volumeID), datastoreRef);
            if (vStorageObject.getConfig() != null && vStorageObject.getConfig().getBacking() != null) {
               BackingInfo backing = vStorageObject.getConfig().getBacking();
               if (backing instanceof DiskFileBackingInfo) {
                  result.uuid = ((DiskFileBackingInfo)backing).getBackingObjectId();
               }
            }

            if (StringUtils.isEmpty(result.uuid)) {
               logger.error("Unable to get vSAN object uuid of volume " + volumeID);
            } else {
               List<VmDiskAssociations> vmDiskAssociations = this.getVolumeVmDiskAssociations(datastoreRef, volumeID, vStorageObjectManager);
               if (vmDiskAssociations.size() > 0) {
                  result.virtualObjectsFilter = VirtualObjectsFilter.VMS;
               }
            }
         } finally {
            if (connection != null) {
               connection.close();
            }

         }

         return result;
      } catch (Throwable var18) {
         if (var4 == null) {
            var4 = var18;
         } else if (var4 != var18) {
            var4.addSuppressed(var18);
         }

         throw var4;
      }
   }

   private List<VmDiskAssociations> getVolumeVmDiskAssociations(ManagedObjectReference datastoreRef, String volumeID, VStorageObjectManager vStorageObjectManager) {
      RetrieveVStorageObjSpec vStorageObjSpec = new RetrieveVStorageObjSpec(new ID(volumeID), datastoreRef);
      Throwable var5 = null;
      Object var6 = null;

      try {
         Measure measure = new Measure("VStorageObjectManager.retrieveVStorageObjectAssociations");

         List var40;
         label452: {
            Throwable var10000;
            label455: {
               ArrayList var39;
               VStorageObjectAssociations[] vStorageObjectAssociations;
               boolean var10001;
               label454: {
                  try {
                     vStorageObjectAssociations = vStorageObjectManager.retrieveVStorageObjectAssociations(new RetrieveVStorageObjSpec[]{vStorageObjSpec});
                     if (!ArrayUtils.isEmpty(vStorageObjectAssociations)) {
                        break label454;
                     }

                     var39 = new ArrayList();
                  } catch (Throwable var37) {
                     var10000 = var37;
                     var10001 = false;
                     break label455;
                  }

                  if (measure != null) {
                     measure.close();
                  }

                  return var39;
               }

               try {
                  if (!ArrayUtils.isEmpty(vStorageObjectAssociations[0].vmDiskAssociations)) {
                     var40 = Arrays.asList(vStorageObjectAssociations[0].vmDiskAssociations);
                     break label452;
                  }
               } catch (Throwable var36) {
                  var10000 = var36;
                  var10001 = false;
                  break label455;
               }

               try {
                  var39 = new ArrayList();
               } catch (Throwable var35) {
                  var10000 = var35;
                  var10001 = false;
                  break label455;
               }

               if (measure != null) {
                  measure.close();
               }

               label427:
               try {
                  return var39;
               } catch (Throwable var34) {
                  var10000 = var34;
                  var10001 = false;
                  break label427;
               }
            }

            var5 = var10000;
            if (measure != null) {
               measure.close();
            }

            throw var5;
         }

         if (measure != null) {
            measure.close();
         }

         return var40;
      } catch (Throwable var38) {
         if (var5 == null) {
            var5 = var38;
         } else if (var5 != var38) {
            var5.addSuppressed(var38);
         }

         throw var5;
      }
   }

   @TsService
   public QueryLabelResult queryLabels(ManagedObjectReference param1, String param2, String param3) {
      // $FF: Couldn't be decompiled
   }

   @TsService
   public VolumeComplianceFailure[] loadComplianceFailures(ManagedObjectReference contextObjectRef, Volume volume) throws VsanUiLocalizableException {
      List<VolumeComplianceFailure> volumeComplianceFailures = new ArrayList();
      Throwable var6 = null;
      ComplianceResult complianceResult = null;

      ComplianceResult[] complianceResults;
      CapabilityObjectSchema[] capabilityObjectSchemas;
      try {
         label250: {
            Measure measure = new Measure("Collect Compliance Failures Results");

            Measure var31;
            try {
               VsanAsyncDataRetriever dataRetriever = this.dataRetrieverFactory.createVsanAsyncDataRetriever(measure, contextObjectRef).loadComplianceResults(volume).loadCapabilityObjectSchema();

               try {
                  complianceResults = dataRetriever.getComplianceResults();
               } catch (Exception var25) {
                  logger.error("Cannot load compliance failures.", var25);
                  throw new VsanUiLocalizableException("vsan.cns.load.compliance.failures.error");
               }

               if (!ArrayUtils.isEmpty(complianceResults)) {
                  try {
                     capabilityObjectSchemas = dataRetriever.getCabalityObjectSchema();
                     break label250;
                  } catch (Exception var24) {
                     logger.error("Cannot load capability object schemas.", var24);
                     throw new VsanUiLocalizableException("vsan.cns.load.compliance.failures.error");
                  }
               }

               VolumeComplianceFailure[] var10000 = (VolumeComplianceFailure[])volumeComplianceFailures.toArray(new VolumeComplianceFailure[0]);
            } finally {
               var31 = measure;
               if (measure != null) {
                  var31 = measure;
                  measure.close();
               }

            }

            return var31;
         }
      } catch (Throwable var27) {
         if (var6 == null) {
            var6 = var27;
         } else if (var6 != var27) {
            var6.addSuppressed(var27);
         }

         throw var6;
      }

      Map<String, String> propertyMetadataMap = this.getMetadataFromCapabilityObjectSchema(capabilityObjectSchemas);
      if (propertyMetadataMap != null && !propertyMetadataMap.isEmpty()) {
         ComplianceResult[] var10 = complianceResults;
         int var30 = complianceResults.length;

         for(int var29 = 0; var29 < var30; ++var29) {
            complianceResult = var10[var29];
            if (!ArrayUtils.isEmpty(complianceResult.violatedPolicies)) {
               PolicyStatus[] var14;
               int var13 = (var14 = complianceResult.violatedPolicies).length;

               for(int var12 = 0; var12 < var13; ++var12) {
                  PolicyStatus policyStatus = var14[var12];
                  List<VolumeComplianceFailure> parsedComplianceFailures = this.parseViolatedPolicyStatuses(policyStatus.getCurrentValue(), policyStatus.getExpectedValue(), propertyMetadataMap);
                  volumeComplianceFailures.addAll(parsedComplianceFailures);
               }
            }
         }

         return (VolumeComplianceFailure[])volumeComplianceFailures.toArray(new VolumeComplianceFailure[0]);
      } else {
         logger.warn("There is no property metadata and there are no labels for the keys of properties");
         return (VolumeComplianceFailure[])volumeComplianceFailures.toArray(new VolumeComplianceFailure[0]);
      }
   }

   private List<VolumeComplianceFailure> parseViolatedPolicyStatuses(CapabilityInstance currentCapabilityInstance, CapabilityInstance expectedCapabilityInstance, Map<String, String> namespaceCapabilityMetadata) {
      List<VolumeComplianceFailure> result = new ArrayList();
      if (expectedCapabilityInstance == null) {
         logger.warn("Expected capability instance is null");
         return result;
      } else {
         ConstraintInstance[] expectedConstraints = expectedCapabilityInstance.getConstraint();
         if (expectedConstraints == null) {
            logger.warn("Expected constraints of capability instance are null");
            return result;
         } else {
            ConstraintInstance[] currentConstraints = new ConstraintInstance[0];
            if (currentCapabilityInstance != null) {
               currentConstraints = currentCapabilityInstance.getConstraint();
            }

            Map<String, String> expectedValuesMap = this.parseConstraints(expectedConstraints);
            Map<String, String> currentValuesMap = new HashMap();
            if (ArrayUtils.isNotEmpty(currentConstraints)) {
               currentValuesMap = this.parseConstraints(currentConstraints);
            }

            Iterator var10 = expectedValuesMap.entrySet().iterator();

            while(var10.hasNext()) {
               Entry<String, String> expectedValueEntry = (Entry)var10.next();
               VolumeComplianceFailure volumeComplianceFailure = new VolumeComplianceFailure();
               volumeComplianceFailure.propertyName = (String)namespaceCapabilityMetadata.get(expectedValueEntry.getKey());
               String expectedValue = (String)expectedValueEntry.getValue();
               String currentValue = (String)((Map)currentValuesMap).get(expectedValueEntry.getKey());
               volumeComplianceFailure.currentValue = currentValue != null ? currentValue : Utils.getLocalizedString("vsan.common.na.label");
               volumeComplianceFailure.expectedValue = expectedValue != null ? expectedValue : Utils.getLocalizedString("vsan.common.na.label");
               result.add(volumeComplianceFailure);
            }

            return result;
         }
      }
   }

   private Map<String, String> parseConstraints(ConstraintInstance[] constraints) {
      Map<String, String> result = new HashMap();
      ConstraintInstance[] var6 = constraints;
      int var5 = constraints.length;

      for(int var4 = 0; var4 < var5; ++var4) {
         ConstraintInstance constraint = var6[var4];
         PropertyInstance[] var10;
         int var9 = (var10 = constraint.propertyInstance).length;

         for(int var8 = 0; var8 < var9; ++var8) {
            PropertyInstance propertyInstance = var10[var8];
            if (propertyInstance.value instanceof DiscreteSet) {
               Object[] constraintRawValues = ((DiscreteSet)propertyInstance.value).getValues();
               String constraintValues = "";
               if (ArrayUtils.isNotEmpty(constraintRawValues)) {
                  constraintValues = Utils.getLocalizedString("vsan.cns.compliance.failures.values", Joiner.on(",").join(constraintRawValues));
               }

               result.put(propertyInstance.id, constraintValues);
            } else {
               result.put(propertyInstance.id, propertyInstance.value.toString());
            }
         }
      }

      return result;
   }

   private Map<String, String> getMetadataFromCapabilityObjectSchema(CapabilityObjectSchema[] capabilityObjectSchemas) {
      Map<String, String> propertyMetadataInfos = new HashMap();
      if (ArrayUtils.isEmpty(capabilityObjectSchemas)) {
         return propertyMetadataInfos;
      } else {
         CapabilityObjectSchema[] var6 = capabilityObjectSchemas;
         int var5 = capabilityObjectSchemas.length;

         for(int var4 = 0; var4 < var5; ++var4) {
            CapabilityObjectSchema capabilityObjectSchema = var6[var4];
            if (!ArrayUtils.isEmpty(capabilityObjectSchema.capabilityMetadataPerCategory)) {
               CapabilityObjectMetadataPerCategory[] var10;
               int var9 = (var10 = capabilityObjectSchema.capabilityMetadataPerCategory).length;

               for(int var8 = 0; var8 < var9; ++var8) {
                  CapabilityObjectMetadataPerCategory capabilityObjectMetadataPerCategory = var10[var8];
                  if (!ArrayUtils.isEmpty(capabilityObjectMetadataPerCategory.capabilityMetadata)) {
                     CapabilityMetadata[] var14;
                     int var13 = (var14 = capabilityObjectMetadataPerCategory.capabilityMetadata).length;

                     for(int var12 = 0; var12 < var13; ++var12) {
                        CapabilityMetadata capabilityMetadata = var14[var12];
                        if (!ArrayUtils.isEmpty(capabilityMetadata.propertyMetadata)) {
                           PropertyMetadata[] var18;
                           int var17 = (var18 = capabilityMetadata.propertyMetadata).length;

                           for(int var16 = 0; var16 < var17; ++var16) {
                              PropertyMetadata propertyMetadata = var18[var16];
                              if (propertyMetadata.getSummary() != null && propertyMetadata.getId() != null && propertyMetadata.getSummary().getLabel() != null) {
                                 propertyMetadataInfos.put(propertyMetadata.getId(), propertyMetadata.getSummary().getLabel());
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         return propertyMetadataInfos;
      }
   }

   @TsService
   public List<CnsHostData> getHostsDataByDatastoreRefs(ManagedObjectReference[] datastoreRefs) throws VsanUiLocalizableException {
      if (ArrayUtils.isEmpty(datastoreRefs)) {
         return new ArrayList();
      } else {
         ArrayList result = new ArrayList();

         try {
            Map<ManagedObjectReference, Boolean> hostAccessibilityMap = this.getHostsAccessibility(datastoreRefs);
            List<CnsHostData> hostsData = this.buildHostsData(hostAccessibilityMap);
            result.addAll(hostsData);
            return result;
         } catch (Exception var5) {
            logger.error("Cannot retrieve mounted hosts data: ", var5);
            throw new VsanUiLocalizableException("vsan.common.generic.error");
         }
      }
   }

   private Map<ManagedObjectReference, Boolean> getHostsAccessibility(ManagedObjectReference[] datastoreRefs) throws Exception {
      Map<ManagedObjectReference, Boolean> hostAccessibilityMap = new HashMap();
      DataServiceResponse dataServiceResponse = QueryUtil.getProperties(datastoreRefs, new String[]{"host"});
      ManagedObjectReference[] var7 = datastoreRefs;
      int var6 = datastoreRefs.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         ManagedObjectReference datastoreRef = var7[var5];
         HostMount[] hostMounts = (HostMount[])dataServiceResponse.getProperty(datastoreRef, "host");
         if (ArrayUtils.isEmpty(hostMounts)) {
            logger.warn("There are no hosts to which the datastore: " + datastoreRef.getValue() + " is mounted");
         } else {
            HostMount[] var12 = hostMounts;
            int var11 = hostMounts.length;

            for(int var10 = 0; var10 < var11; ++var10) {
               HostMount hostMount = var12[var10];
               if (hostMount.getMountInfo() != null) {
                  hostAccessibilityMap.put(hostMount.getKey(), hostMount.getMountInfo().accessible);
               }
            }
         }
      }

      return hostAccessibilityMap;
   }

   private List<CnsHostData> buildHostsData(Map<ManagedObjectReference, Boolean> hostAccessibilityMap) throws Exception {
      List<CnsHostData> result = new ArrayList();
      if (MapUtils.isEmpty(hostAccessibilityMap)) {
         return result;
      } else {
         ManagedObjectReference[] hostMoRefs = (ManagedObjectReference[])hostAccessibilityMap.keySet().toArray(new ManagedObjectReference[0]);
         String[] hostProperties = new String[]{"name", "primaryIconId"};
         DataServiceResponse dataServiceResponse = QueryUtil.getProperties(hostMoRefs, hostProperties);
         ManagedObjectReference[] var9 = hostMoRefs;
         int var8 = hostMoRefs.length;

         for(int var7 = 0; var7 < var8; ++var7) {
            ManagedObjectReference hostRef = var9[var7];
            String hostName = (String)dataServiceResponse.getProperty(hostRef, "name");
            String hostIconId = (String)dataServiceResponse.getProperty(hostRef, "primaryIconId");
            Boolean isDatastoreAccessibleFromHost = (Boolean)hostAccessibilityMap.get(hostRef);
            CnsDatastoreAccessibilityStatus hostAccessibility = isDatastoreAccessibleFromHost ? CnsDatastoreAccessibilityStatus.accessible : CnsDatastoreAccessibilityStatus.notAccessible;
            CnsHostData hostData = new CnsHostData(hostName, hostIconId, hostAccessibility);
            result.add(hostData);
         }

         return result;
      }
   }

   @TsService
   public ReconfigOutcome[] reapplyStoragePolicy(ManagedObjectReference param1, Volume[] param2) {
      // $FF: Couldn't be decompiled
   }

   class DatastoreMetadata {
      String name;
      String type;
      String datastoreUrl;
   }
}
