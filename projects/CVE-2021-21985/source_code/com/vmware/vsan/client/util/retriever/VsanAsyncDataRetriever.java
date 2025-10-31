package com.vmware.vsan.client.util.retriever;

import com.vmware.vim.binding.pbm.capability.provider.CapabilityObjectSchema;
import com.vmware.vim.binding.pbm.compliance.ComplianceResult;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiLUN;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTarget;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectIdentityAndHealth;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectInformation;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfEntityType;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfNodeInformation;
import com.vmware.vim.vsan.binding.vim.vsan.ConfigInfoEx;
import com.vmware.vim.vsan.binding.vim.vsan.FileShare;
import com.vmware.vim.vsan.binding.vim.vsan.VsanFileServicePreflightCheckResult;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.services.cns.model.Volume;
import com.vmware.vsan.client.services.common.PermissionService;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.PbmClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class VsanAsyncDataRetriever {
   private static final Log logger = LogFactory.getLog(VsanAsyncDataRetriever.class);
   private Measure measure;
   private ManagedObjectReference clusterRef;
   private Map<VsanAsyncDataRetriever.DataRetrieverType, DataRetriever> dataRetrievers;
   private final VcClient vcClient;
   private final VmodlHelper vmodlHelper;
   private final PbmClient pbmClient;
   private final PermissionService permissionService;

   public VsanAsyncDataRetriever(Measure measure, ManagedObjectReference clusterRef, VcClient vcClient, VmodlHelper vmodlHelper, PbmClient pbmClient, PermissionService permissionService) {
      Validate.notNull(measure);
      Validate.notNull(clusterRef);
      Validate.notNull(vcClient);
      Validate.notNull(vmodlHelper);
      Validate.notNull(pbmClient);
      this.measure = measure;
      this.clusterRef = clusterRef;
      this.vcClient = vcClient;
      this.vmodlHelper = vmodlHelper;
      this.pbmClient = pbmClient;
      this.permissionService = permissionService;
      this.dataRetrievers = new HashMap();
   }

   public VsanAsyncDataRetriever loadObjectIdentities() {
      return this.loadObjectIdentities((Set)null);
   }

   public VsanAsyncDataRetriever loadObjectIdentities(Set<String> uuids) {
      if (!VsanCapabilityUtils.getCapabilities(this.clusterRef).isObjectIdentitiesSupported) {
         throw new VsanUiLocalizableException("vsan.virtualObjects.error.hostVersion");
      } else {
         this.addDataRetriever(VsanAsyncDataRetriever.DataRetrieverType.IDENTITIES, new ObjectIdentitiesDataRetriever(this.clusterRef, this.measure, uuids));
         return this;
      }
   }

   public VsanAsyncDataRetriever loadObjectInformation(Set<String> uuids) {
      Validate.notNull(uuids);
      this.addDataRetriever(VsanAsyncDataRetriever.DataRetrieverType.INFOS, new ObjectInformationDataRetriever(this.clusterRef, this.measure, uuids));
      return this;
   }

   public VsanAsyncDataRetriever loadIscsiTargets() {
      this.addDataRetriever(VsanAsyncDataRetriever.DataRetrieverType.ISCSI_TARGETS, new IscsiTargetsDataRetriever(this.clusterRef, this.measure));
      return this;
   }

   public VsanAsyncDataRetriever loadIscsiLuns() {
      this.addDataRetriever(VsanAsyncDataRetriever.DataRetrieverType.ISCSI_LUNS, new IscsiLunsDataRetriever(this.clusterRef, this.measure));
      return this;
   }

   public VsanAsyncDataRetriever loadFileShares() {
      this.addDataRetriever(VsanAsyncDataRetriever.DataRetrieverType.FILE_SHARES, new FileSharesDataRetriever(this.clusterRef, this.measure));
      return this;
   }

   public VsanAsyncDataRetriever loadFileServicePrecheckResult() {
      this.addDataRetriever(VsanAsyncDataRetriever.DataRetrieverType.FILE_SERVICE_PRECHECK, new FileServicePrecheckDataRetriever(this.clusterRef, this.measure));
      return this;
   }

   public VsanAsyncDataRetriever loadClusterUuids() {
      this.addDataRetriever(VsanAsyncDataRetriever.DataRetrieverType.CLUSTER_UUIDS, new ClusterUuidsDataRetriever(this.clusterRef, this.measure, this.vcClient, this.vmodlHelper));
      return this;
   }

   public VsanAsyncDataRetriever loadComplianceResults(Volume volume) {
      this.addDataRetriever(VsanAsyncDataRetriever.DataRetrieverType.COMPLIANCE_RESULTS, new ComplianceResultDataRetriever(this.clusterRef, this.measure, this.pbmClient, volume));
      return this;
   }

   public VsanAsyncDataRetriever loadCapabilityObjectSchema() {
      this.addDataRetriever(VsanAsyncDataRetriever.DataRetrieverType.CAPABILITY_OBJECT_SCHEMA, new CapabilityObjectSchemaDataRetriever(this.clusterRef, this.measure, this.pbmClient));
      return this;
   }

   public VsanAsyncDataRetriever loadSupportedEntityTypes() {
      this.addDataRetriever(VsanAsyncDataRetriever.DataRetrieverType.SUPPORTED_ENTITY_TYPES, new SupportedEntityTypesDataRetriever(this.clusterRef, this.measure));
      return this;
   }

   public VsanAsyncDataRetriever loadNodeInformation() {
      this.addDataRetriever(VsanAsyncDataRetriever.DataRetrieverType.NODE_INFORMATION, new NodeInformationDataRetriever(this.clusterRef, this.measure));
      return this;
   }

   public VsanAsyncDataRetriever loadConfigInfoEx() {
      this.addDataRetriever(VsanAsyncDataRetriever.DataRetrieverType.CONFIG_INFO, new ConfigInfoExDataRetriever(this.clusterRef, this.measure));
      return this;
   }

   public VsanAsyncDataRetriever loadStatsObjectInformation() {
      this.addDataRetriever(VsanAsyncDataRetriever.DataRetrieverType.STATS_OBJECT_INFO, new StatsObjectInformationDataRetriever(this.clusterRef, this.measure));
      return this;
   }

   public VsanAsyncDataRetriever loadStoragePolicies() {
      this.addDataRetriever(VsanAsyncDataRetriever.DataRetrieverType.POLICIES, new PoliciesDataRetriever(this.clusterRef, this.measure, this.pbmClient, this.permissionService));
      return this;
   }

   public VsanObjectIdentityAndHealth getObjectIdentities() throws ExecutionException, InterruptedException {
      return (VsanObjectIdentityAndHealth)this.getResult(VsanAsyncDataRetriever.DataRetrieverType.IDENTITIES);
   }

   public VsanObjectInformation[] getObjectInformation() throws ExecutionException, InterruptedException {
      return (VsanObjectInformation[])this.getResult(VsanAsyncDataRetriever.DataRetrieverType.INFOS);
   }

   public VsanIscsiTarget[] getIscsiTargets() throws ExecutionException, InterruptedException {
      return (VsanIscsiTarget[])this.getResult(VsanAsyncDataRetriever.DataRetrieverType.ISCSI_TARGETS);
   }

   public VsanIscsiLUN[] getIscsiLuns() throws ExecutionException, InterruptedException {
      return (VsanIscsiLUN[])this.getResult(VsanAsyncDataRetriever.DataRetrieverType.ISCSI_LUNS);
   }

   public FileShare[] getFileShares() throws ExecutionException, InterruptedException {
      return (FileShare[])this.getResult(VsanAsyncDataRetriever.DataRetrieverType.FILE_SHARES);
   }

   public VsanFileServicePreflightCheckResult getFileServicePrecheckResult() throws ExecutionException, InterruptedException {
      return (VsanFileServicePreflightCheckResult)this.getResult(VsanAsyncDataRetriever.DataRetrieverType.FILE_SERVICE_PRECHECK);
   }

   public Set<String> getClusterUuids() throws ExecutionException, InterruptedException {
      return (Set)this.getResult(VsanAsyncDataRetriever.DataRetrieverType.CLUSTER_UUIDS);
   }

   public ComplianceResult[] getComplianceResults() throws ExecutionException, InterruptedException {
      return (ComplianceResult[])this.getResult(VsanAsyncDataRetriever.DataRetrieverType.COMPLIANCE_RESULTS);
   }

   public VsanPerfEntityType[] getSupportedEntityTypes() throws ExecutionException, InterruptedException {
      return (VsanPerfEntityType[])this.getResult(VsanAsyncDataRetriever.DataRetrieverType.SUPPORTED_ENTITY_TYPES);
   }

   public VsanPerfNodeInformation[] getNodeInformation() throws ExecutionException, InterruptedException {
      return (VsanPerfNodeInformation[])this.getResult(VsanAsyncDataRetriever.DataRetrieverType.NODE_INFORMATION);
   }

   public ConfigInfoEx getConfigInfoEx() throws ExecutionException, InterruptedException {
      return (ConfigInfoEx)this.getResult(VsanAsyncDataRetriever.DataRetrieverType.CONFIG_INFO);
   }

   public VsanObjectInformation getStatsObjectInformation() throws ExecutionException, InterruptedException {
      return (VsanObjectInformation)this.getResult(VsanAsyncDataRetriever.DataRetrieverType.STATS_OBJECT_INFO);
   }

   public CapabilityObjectSchema[] getCabalityObjectSchema() throws ExecutionException, InterruptedException {
      return (CapabilityObjectSchema[])this.getResult(VsanAsyncDataRetriever.DataRetrieverType.CAPABILITY_OBJECT_SCHEMA);
   }

   public Map<String, String> getStoragePolicies() throws ExecutionException, InterruptedException {
      return (Map)this.getResult(VsanAsyncDataRetriever.DataRetrieverType.POLICIES);
   }

   private void addDataRetriever(VsanAsyncDataRetriever.DataRetrieverType type, DataRetriever<?> dataRetriever) {
      if (this.dataRetrievers.containsKey(type)) {
         throw new IllegalStateException("'" + type + "' data retriever has alread been registered!");
      } else {
         dataRetriever.start();
         this.dataRetrievers.put(type, dataRetriever);
         logger.debug("Registered retriever: " + type);
      }
   }

   private <T> T getResult(VsanAsyncDataRetriever.DataRetrieverType type) throws ExecutionException, InterruptedException {
      return this.getDataRetriever(type).getResult();
   }

   private DataRetriever getDataRetriever(VsanAsyncDataRetriever.DataRetrieverType type) {
      DataRetriever dataRetriever = (DataRetriever)this.dataRetrievers.get(type);
      if (dataRetriever == null) {
         throw new IllegalStateException("No '" + type.toString() + "'DataRetriever found!");
      } else {
         return dataRetriever;
      }
   }

   private static enum DataRetrieverType {
      IDENTITIES,
      INFOS,
      ISCSI_TARGETS,
      ISCSI_LUNS,
      FILE_SHARES,
      FILE_SERVICE_PRECHECK,
      CLUSTER_UUIDS,
      COMPLIANCE_RESULTS,
      CAPABILITY_OBJECT_SCHEMA,
      SUPPORTED_ENTITY_TYPES,
      NODE_INFORMATION,
      CONFIG_INFO,
      STATS_OBJECT_INFO,
      POLICIES;
   }
}
