package com.vmware.vsan.client.services.evacuationstatus;

import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.data.PropertySpec;
import com.vmware.vise.data.query.DataServiceExtensionRegistry;
import com.vmware.vise.data.query.PropertyProviderAdapter;
import com.vmware.vise.data.query.PropertyRequestSpec;
import com.vmware.vise.data.query.ResultItem;
import com.vmware.vise.data.query.ResultSet;
import com.vmware.vise.data.query.TypeInfo;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EvacuationStatusPropertyProviderAdapter implements PropertyProviderAdapter {
   private static final String VSAN_HOST_EVACUATION_STATUS_SUPPORTED_ON_VC = "evacuationStatusSupportedOnVc";
   private static final String VSAN_HOST_EVACUATION_STATUS_SUPPORTED_ON_CLUSTER = "evacuationStatusSupportedOnCluster";
   private static final Log logger = LogFactory.getLog(EvacuationStatusPropertyProviderAdapter.class);

   @Autowired
   public void setDataServiceExtensionRegistry(DataServiceExtensionRegistry registry) {
      Validate.notNull(registry);
      TypeInfo clusterInfo = new TypeInfo();
      clusterInfo.type = ClusterComputeResource.class.getSimpleName();
      clusterInfo.properties = new String[]{"evacuationStatusSupportedOnVc", "evacuationStatusSupportedOnCluster"};
      TypeInfo[] providedProperties = new TypeInfo[]{clusterInfo};
      registry.registerDataAdapter(this, providedProperties);
   }

   public ResultSet getProperties(PropertyRequestSpec propertyRequest) {
      ResultSet result = new ResultSet();
      result.items = new ResultItem[0];
      PropertySpec[] var6;
      int var5 = (var6 = propertyRequest.properties).length;

      for(int var4 = 0; var4 < var5; ++var4) {
         PropertySpec propertySpec = var6[var4];
         String[] var10;
         int var9 = (var10 = propertySpec.propertyNames).length;

         for(int var8 = 0; var8 < var9; ++var8) {
            String propertyName = var10[var8];

            try {
               switch(propertyName.hashCode()) {
               case -1923978507:
                  if (propertyName.equals("evacuationStatusSupportedOnVc")) {
                     result.items = (ResultItem[])ArrayUtils.addAll(result.items, this.isPreCheckSupported(propertyRequest.objects));
                     continue;
                  }
                  break;
               case -1566629198:
                  if (propertyName.equals("evacuationStatusSupportedOnCluster")) {
                     result.items = (ResultItem[])ArrayUtils.addAll(result.items, this.isPreCheckSupportedOnCluster(propertyRequest.objects));
                     continue;
                  }
               }

               throw new IllegalArgumentException("Unexpected property name: " + propertyName);
            } catch (Exception var12) {
               logger.error("Incorrect property requested : ", var12);
               result.error = var12;
            }
         }
      }

      return result;
   }

   private ResultItem[] isPreCheckSupported(Object[] targetObjects) {
      ManagedObjectReference[] clusterRefs = (ManagedObjectReference[])Arrays.copyOf(targetObjects, targetObjects.length, ManagedObjectReference[].class);
      ArrayList<ResultItem> result = new ArrayList();
      ManagedObjectReference[] var7 = clusterRefs;
      int var6 = clusterRefs.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         ManagedObjectReference clusterRef = var7[var5];
         ManagedObjectReference vcRef = VmodlHelper.getRootFolder(clusterRef.getServerGuid());
         if (!VsanCapabilityUtils.isEvacuationStatusSupportedOnVc(vcRef)) {
            result.add(QueryUtil.createResultItem("evacuationStatusSupportedOnVc", false, clusterRef));
         } else {
            result.add(QueryUtil.createResultItem("evacuationStatusSupportedOnVc", true, clusterRef));
         }
      }

      return (ResultItem[])result.toArray(new ResultItem[0]);
   }

   private ResultItem[] isPreCheckSupportedOnCluster(Object[] targetObjects) {
      ManagedObjectReference[] clusterRefs = (ManagedObjectReference[])Arrays.copyOf(targetObjects, targetObjects.length, ManagedObjectReference[].class);
      ArrayList<ResultItem> result = new ArrayList();
      ManagedObjectReference[] var7 = clusterRefs;
      int var6 = clusterRefs.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         ManagedObjectReference clusterRef = var7[var5];
         result.add(QueryUtil.createResultItem("evacuationStatusSupportedOnCluster", VsanCapabilityUtils.isEvacuationStatusSupportedOnCluster(clusterRef), clusterRef));
      }

      return (ResultItem[])result.toArray(new ResultItem[0]);
   }
}
