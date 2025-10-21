package com.vmware.vsphere.client.vsan.config;

import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.Datastore;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterConfigSystem;
import com.vmware.vim.vsan.binding.vim.vsan.ConfigInfoEx;
import com.vmware.vise.data.query.DataServiceExtensionRegistry;
import com.vmware.vise.data.query.PropertyProviderAdapter;
import com.vmware.vise.data.query.PropertyRequestSpec;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.ResultItem;
import com.vmware.vise.data.query.ResultSet;
import com.vmware.vise.data.query.TypeInfo;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.services.encryption.EncryptionStatus;
import com.vmware.vsphere.client.vsan.base.util.BaseUtils;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.data.EncryptionState;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class VsanConfigPropertyProviderAdapter implements PropertyProviderAdapter {
   private static final Log logger = LogFactory.getLog(VsanConfigPropertyProviderAdapter.class);
   private static final VsanProfiler _profiler = new VsanProfiler(VsanConfigPropertyProviderAdapter.class);
   public static final String VSAN_CONFIG_INFO = "vsanConfigInfo";
   public static final String VSAN_ENCRYPTION_STATUS = "vsanEncryptionStatus";
   public static final String VSAN_RESYNC_THROTTLING = "vsanResyncThrottling";

   public VsanConfigPropertyProviderAdapter(DataServiceExtensionRegistry registry) {
      Validate.notNull(registry);
      String[] properties = new String[]{"vsanConfigInfo", "vsanEncryptionStatus", "dataEfficiencyStatus", "vsanResyncThrottling"};
      TypeInfo clusterInfo = this.createTypeInfo(ClusterComputeResource.class.getSimpleName(), properties);
      TypeInfo datastoreInfo = this.createTypeInfo(Datastore.class.getSimpleName(), properties);
      TypeInfo[] providedProperties = new TypeInfo[]{clusterInfo, datastoreInfo};
      registry.registerDataAdapter(this, providedProperties);
   }

   private TypeInfo createTypeInfo(String type, String[] properties) {
      TypeInfo typeInfo = new TypeInfo();
      typeInfo.type = type;
      typeInfo.properties = properties;
      return typeInfo;
   }

   public ResultSet getProperties(PropertyRequestSpec propertyRequest) {
      if (!this.isValidRequest(propertyRequest)) {
         ResultSet result = new ResultSet();
         result.totalMatchedObjectCount = 0;
         return result;
      } else {
         List<ResultItem> resultItems = new ArrayList();
         Object[] var6;
         int var5 = (var6 = propertyRequest.objects).length;

         for(int var4 = 0; var4 < var5; ++var4) {
            Object objectRef = var6[var4];
            ManagedObjectReference clusterRef = null;
            if (objectRef instanceof ManagedObjectReference) {
               clusterRef = BaseUtils.getCluster((ManagedObjectReference)objectRef);
            }

            if (clusterRef != null) {
               ConfigInfoEx config = null;
               if (this.shouldRequestConfigInfo(clusterRef, propertyRequest)) {
                  VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);

                  ResultSet resultSet;
                  try {
                     Throwable var10 = null;
                     resultSet = null;

                     try {
                        VsanProfiler.Point point = _profiler.point("vsanConfigSystem.getConfigInfoEx");

                        try {
                           config = vsanConfigSystem.getConfigInfoEx(clusterRef);
                        } finally {
                           if (point != null) {
                              point.close();
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
                  } catch (Exception var21) {
                     logger.error("Could not retrieve cluster's config info", var21);
                     resultSet = new ResultSet();
                     resultSet.error = var21;
                     return resultSet;
                  }
               }

               List<PropertyValue> propValues = new ArrayList();
               PropertyValue propValue;
               if (QueryUtil.isAnyPropertyRequested(propertyRequest.properties, "vsanConfigInfo")) {
                  propValue = QueryUtil.newProperty("vsanConfigInfo", this.getVsanConfigValue(clusterRef, config));
                  propValue.resourceObject = objectRef;
                  propValues.add(propValue);
               }

               if (QueryUtil.isAnyPropertyRequested(propertyRequest.properties, "dataEfficiencyStatus")) {
                  propValue = QueryUtil.newProperty("dataEfficiencyStatus", this.getDedupStatusValue(clusterRef, config));
                  propValue.resourceObject = objectRef;
                  propValues.add(propValue);
               }

               if (QueryUtil.isAnyPropertyRequested(propertyRequest.properties, "vsanEncryptionStatus")) {
                  propValue = QueryUtil.newProperty("vsanEncryptionStatus", this.getEncryptionStatusValue(clusterRef, config));
                  propValue.resourceObject = objectRef;
                  propValues.add(propValue);
               }

               if (QueryUtil.isAnyPropertyRequested(propertyRequest.properties, "vsanResyncThrottling")) {
                  propValue = QueryUtil.newProperty("vsanResyncThrottling", this.getResyncThrottlingStatusValue(clusterRef, config));
                  propValue.resourceObject = objectRef;
                  propValues.add(propValue);
               }

               ResultItem resultItem = QueryUtil.newResultItem(objectRef, (PropertyValue[])propValues.toArray(new PropertyValue[propValues.size()]));
               resultItems.add(resultItem);
            }
         }

         ResultSet resultSet = QueryUtil.newResultSet((ResultItem[])resultItems.toArray(new ResultItem[resultItems.size()]));
         return resultSet;
      }
   }

   private ConfigInfoEx getVsanConfigValue(ManagedObjectReference clusterRef, ConfigInfoEx config) {
      return VsanCapabilityUtils.isClusterConfigSystemSupportedOnVc(clusterRef) ? config : null;
   }

   private boolean getDedupStatusValue(ManagedObjectReference clusterRef, ConfigInfoEx config) {
      if (!VsanCapabilityUtils.isDeduplicationAndCompressionSupportedOnVc(clusterRef)) {
         return false;
      } else {
         boolean enabled = false;
         if (config != null && config.dataEfficiencyConfig != null) {
            enabled = config.dataEfficiencyConfig.isDedupEnabled();
         }

         return enabled;
      }
   }

   private EncryptionStatus getEncryptionStatusValue(ManagedObjectReference clusterRef, ConfigInfoEx config) {
      EncryptionStatus status = new EncryptionStatus();
      status.state = EncryptionState.Disabled;
      if (VsanCapabilityUtils.isEncryptionSupportedOnVc(clusterRef) && config != null && config.dataEncryptionConfig != null) {
         status.state = config.dataEncryptionConfig.encryptionEnabled ? EncryptionState.Enabled : EncryptionState.Disabled;
         status.kmipClusterId = config.dataEncryptionConfig.kmsProviderId == null ? "" : config.dataEncryptionConfig.kmsProviderId.id;
         status.eraseDisksBeforeUse = config.dataEncryptionConfig.eraseDisksBeforeUse;
         if (status.state == EncryptionState.Enabled && "".equals(status.kmipClusterId)) {
            status.state = EncryptionState.EnabledNoKmip;
         }

         return status;
      } else {
         return status;
      }
   }

   private int getResyncThrottlingStatusValue(ManagedObjectReference clusterRef, ConfigInfoEx config) {
      int result = -1;
      if (!VsanCapabilityUtils.isResyncThrottlingSupported(clusterRef)) {
         return result;
      } else {
         if (config != null && config.resyncIopsLimitConfig != null) {
            result = config.resyncIopsLimitConfig.resyncIops;
         }

         return result;
      }
   }

   private boolean shouldRequestConfigInfo(ManagedObjectReference clusterRef, PropertyRequestSpec propertyRequest) {
      boolean shouldRequestVsanConfig = false;
      if (QueryUtil.isAnyPropertyRequested(propertyRequest.properties, "vsanConfigInfo") && VsanCapabilityUtils.isClusterConfigSystemSupportedOnVc(clusterRef)) {
         shouldRequestVsanConfig = true;
      } else if (QueryUtil.isAnyPropertyRequested(propertyRequest.properties, "dataEfficiencyStatus") && VsanCapabilityUtils.isDeduplicationAndCompressionSupportedOnVc(clusterRef)) {
         shouldRequestVsanConfig = true;
      } else if (QueryUtil.isAnyPropertyRequested(propertyRequest.properties, "vsanEncryptionStatus") && VsanCapabilityUtils.isEncryptionSupportedOnVc(clusterRef)) {
         shouldRequestVsanConfig = true;
      } else if (QueryUtil.isAnyPropertyRequested(propertyRequest.properties, "vsanResyncThrottling") && VsanCapabilityUtils.isResyncThrottlingSupported(clusterRef)) {
         shouldRequestVsanConfig = true;
      }

      return shouldRequestVsanConfig;
   }

   private boolean isValidRequest(PropertyRequestSpec propertyRequest) {
      if (propertyRequest == null) {
         return false;
      } else if (!ArrayUtils.isEmpty(propertyRequest.objects) && !ArrayUtils.isEmpty(propertyRequest.properties)) {
         return true;
      } else {
         logger.error("Property provider adapter got a null or empty list of properties or objects");
         return false;
      }
   }
}
