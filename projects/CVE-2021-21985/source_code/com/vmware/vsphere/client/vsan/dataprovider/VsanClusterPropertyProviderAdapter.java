package com.vmware.vsphere.client.vsan.dataprovider;

import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterConfigSystem;
import com.vmware.vim.vsan.binding.vim.vsan.ConfigInfoEx;
import com.vmware.vise.data.PropertySpec;
import com.vmware.vise.data.query.DataServiceExtensionRegistry;
import com.vmware.vise.data.query.PropertyProviderAdapter;
import com.vmware.vise.data.query.PropertyRequestSpec;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.ResultItem;
import com.vmware.vise.data.query.ResultSet;
import com.vmware.vise.data.query.TypeInfo;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VsanClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vsan.VsanConnection;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class VsanClusterPropertyProviderAdapter implements PropertyProviderAdapter {
   private static final Log _logger = LogFactory.getLog(VsanClusterPropertyProviderAdapter.class);
   private static final String PROPERTY_IS_FILE_SERVICE_ENABLED = "isFileServiceEnabled";
   private static final String PROPERTY_HAS_ELIGIBLE_HOSTS = "hasVsanEligibleHosts";
   private static final String PROPERTY_IS_ISCSI_TARGETS_SUPPORTED_ON_VC = "isIscsiTargetsSupportedOnVc";
   private static final String PROPERTY_IS_ISCSI_TARGETS_ENABLED = "isIscsiTargetsEnabled";
   @Autowired
   private VsanClient vsanClient;

   public VsanClusterPropertyProviderAdapter(DataServiceExtensionRegistry registry) {
      Validate.notNull(registry);
      String[] clusterProperties = new String[]{"hasVsanEligibleHosts", "isIscsiTargetsSupportedOnVc", "isFileServiceEnabled", "isIscsiTargetsEnabled"};
      TypeInfo clusterInfo = new TypeInfo();
      clusterInfo.type = ClusterComputeResource.class.getSimpleName();
      clusterInfo.properties = clusterProperties;
      TypeInfo[] providedProperties = new TypeInfo[]{clusterInfo};
      registry.registerDataAdapter(this, providedProperties);
   }

   public ResultSet getProperties(PropertyRequestSpec propertyRequest) {
      if (!QueryUtil.isValidRequest(propertyRequest)) {
         ResultSet result = new ResultSet();
         result.totalMatchedObjectCount = 0;
         return result;
      } else {
         List<ResultItem> resultItems = new ArrayList();
         Object[] var6;
         int var5 = (var6 = propertyRequest.objects).length;

         for(int var4 = 0; var4 < var5; ++var4) {
            Object objectRef = var6[var4];
            ManagedObjectReference moRef = (ManagedObjectReference)objectRef;
            if (objectRef != null) {
               ResultItem resultItem = null;
               if (ClusterComputeResource.class.getSimpleName().equals(moRef.getType())) {
                  PropertyValue[] clusterProperties = this.getClusterProperties(propertyRequest.properties, objectRef);
                  resultItem = QueryUtil.newResultItem(objectRef, clusterProperties);
               }

               resultItems.add(resultItem);
            }
         }

         ResultSet resultSet = QueryUtil.newResultSet((ResultItem[])resultItems.toArray(new ResultItem[resultItems.size()]));
         return resultSet;
      }
   }

   private PropertyValue[] getClusterProperties(PropertySpec[] properties, Object objectRef) {
      List<PropertyValue> propValues = new ArrayList();
      ManagedObjectReference clusterRef = (ManagedObjectReference)objectRef;
      if (QueryUtil.isAnyPropertyRequested(properties, "hasVsanEligibleHosts")) {
         Object hostCount = 0;

         try {
            hostCount = (Number)QueryUtil.getProperty(clusterRef, "host._length", (Object)null);
         } catch (Exception var9) {
            _logger.warn("Failed to check hosts in cluster, assuming empty: " + clusterRef, var9);
         }

         PropertyValue propValue = QueryUtil.newProperty("hasVsanEligibleHosts", ((Number)hostCount).intValue() > 0);
         propValue.resourceObject = objectRef;
         propValues.add(propValue);
      }

      if (QueryUtil.isAnyPropertyRequested(properties, "isIscsiTargetsSupportedOnVc")) {
         PropertyValue propValue = QueryUtil.newProperty("isIscsiTargetsSupportedOnVc", VsanCapabilityUtils.isIscsiTargetsSupportedOnVc(clusterRef));
         propValue.resourceObject = objectRef;
         propValues.add(propValue);
      }

      if (QueryUtil.isAnyPropertyRequested(properties, "isFileServiceEnabled", "isIscsiTargetsEnabled")) {
         ConfigInfoEx vsanConfig = null;

         try {
            vsanConfig = this.getVsanConfig(clusterRef);
         } catch (Exception var8) {
            _logger.error("Cannot retrieve vSAN configuration for cluster: " + clusterRef, var8);
         }

         PropertyValue propValue;
         boolean result;
         if (QueryUtil.isAnyPropertyRequested(properties, "isFileServiceEnabled")) {
            result = vsanConfig != null && BooleanUtils.isTrue(vsanConfig.enabled) && vsanConfig.fileServiceConfig != null && BooleanUtils.isTrue(vsanConfig.fileServiceConfig.enabled);
            propValue = QueryUtil.newProperty("isFileServiceEnabled", result);
            propValue.resourceObject = clusterRef;
            propValues.add(propValue);
         }

         if (QueryUtil.isAnyPropertyRequested(properties, "isIscsiTargetsEnabled")) {
            result = vsanConfig != null && BooleanUtils.isTrue(vsanConfig.enabled) && vsanConfig.iscsiConfig != null && BooleanUtils.isTrue(vsanConfig.iscsiConfig.enabled);
            propValue = QueryUtil.newProperty("isIscsiTargetsEnabled", result);
            propValue.resourceObject = clusterRef;
            propValues.add(propValue);
         }
      }

      return (PropertyValue[])propValues.toArray(new PropertyValue[0]);
   }

   private ConfigInfoEx getVsanConfig(ManagedObjectReference clusterRef) throws Exception {
      Throwable var2 = null;
      Object var3 = null;

      try {
         VsanConnection conn = this.vsanClient.getConnection(clusterRef.getServerGuid());

         Throwable var10000;
         label173: {
            boolean var10001;
            ConfigInfoEx var18;
            try {
               VsanVcClusterConfigSystem vsanConfigSystem = conn.getVsanConfigSystem();
               var18 = vsanConfigSystem.getConfigInfoEx(clusterRef);
            } catch (Throwable var16) {
               var10000 = var16;
               var10001 = false;
               break label173;
            }

            if (conn != null) {
               conn.close();
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
         if (conn != null) {
            conn.close();
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
}
