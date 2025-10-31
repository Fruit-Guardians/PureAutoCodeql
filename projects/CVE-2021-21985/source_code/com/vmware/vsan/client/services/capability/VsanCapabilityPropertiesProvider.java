package com.vmware.vsan.client.services.capability;

import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.Datacenter;
import com.vmware.vim.binding.vim.Datastore;
import com.vmware.vim.binding.vim.Folder;
import com.vmware.vim.binding.vim.VirtualMachine;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.data.query.DataServiceExtensionRegistry;
import com.vmware.vise.data.query.PropertyProviderAdapter;
import com.vmware.vise.data.query.PropertyRequestSpec;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.ResultItem;
import com.vmware.vise.data.query.ResultSet;
import com.vmware.vise.data.query.TypeInfo;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.Validate;
import org.springframework.beans.factory.annotation.Autowired;

public class VsanCapabilityPropertiesProvider implements PropertyProviderAdapter {
   private static final String PROPERTY_IS_IMPROVED_CAPACITY_MONITORING_SUPPORTED = "isImprovedCapacityMonitoringSupportedOnVc";
   private static final String PROPERTY_IS_VM_LEVEL_CAPACITY_MONITORING_SUPPORTED = "isVmLevelCapacityMonitoringSupported";
   private static final String PROPERTY_CNS_SUPPORTED_ON_VC = "isCnsVolumesSupportedOnVc";
   private static final String PROPERTY_IS_VSAN_ARCHIVE_DP_SUPPORTED = "isVsanArchiveDpSupported";
   private static final String PROPERTY_IS_VSAN_REMOTE_DP_SUPPORTED = "isVsanRemoteDpSupported";
   private static final String PROPERTY_IS_VSAN_NESTED_FDS_SUPPORTED = "isVsanNestedFdsSupported";
   @Autowired
   protected VmodlHelper vmodlHelper;

   public VsanCapabilityPropertiesProvider(DataServiceExtensionRegistry registry) {
      Validate.notNull(registry);
      List<TypeInfo> capabilityTypeInfos = new ArrayList();
      capabilityTypeInfos.addAll(this.getCapacityTypeInfos());
      capabilityTypeInfos.addAll(this.getDpTypeInfos());
      capabilityTypeInfos.addAll(this.getCnsTypeInfos());
      capabilityTypeInfos.addAll(this.getNestedFdsInfos());
      registry.registerDataAdapter(this, (TypeInfo[])capabilityTypeInfos.toArray(new TypeInfo[0]));
   }

   private List<TypeInfo> getCapacityTypeInfos() {
      String[] capacityProperties = new String[]{"isImprovedCapacityMonitoringSupportedOnVc", "isVmLevelCapacityMonitoringSupported"};
      TypeInfo vmTypeInfo = this.getTypeInfo(VirtualMachine.class, capacityProperties);
      TypeInfo clusterTypeInfo = this.getTypeInfo(ClusterComputeResource.class, capacityProperties);
      return new ArrayList<TypeInfo>(vmTypeInfo, clusterTypeInfo) {
         {
            this.add(var2);
            this.add(var3);
         }
      };
   }

   private List<TypeInfo> getDpTypeInfos() {
      String[] dpProperties = new String[]{"isVsanArchiveDpSupported", "isVsanRemoteDpSupported"};
      TypeInfo clusterTypeInfo = this.getTypeInfo(ClusterComputeResource.class, dpProperties);
      TypeInfo folderTypeInfo = this.getTypeInfo(Folder.class, dpProperties);
      return new ArrayList<TypeInfo>(clusterTypeInfo, folderTypeInfo) {
         {
            this.add(var2);
            this.add(var3);
         }
      };
   }

   private List<TypeInfo> getNestedFdsInfos() {
      String[] nestedFdsProperties = new String[]{"isVsanNestedFdsSupported"};
      TypeInfo folderTypeInfo = this.getTypeInfo(Folder.class, nestedFdsProperties);
      return new ArrayList<TypeInfo>(folderTypeInfo) {
         {
            this.add(var2);
         }
      };
   }

   private List<TypeInfo> getCnsTypeInfos() {
      String[] cnsProperties = new String[]{"isCnsVolumesSupportedOnVc"};
      TypeInfo folderTypeInfo = this.getTypeInfo(Folder.class, cnsProperties);
      TypeInfo datacenterTypeInfo = this.getTypeInfo(Datacenter.class, cnsProperties);
      TypeInfo clusterTypeInfo = this.getTypeInfo(ClusterComputeResource.class, cnsProperties);
      TypeInfo datastoreTypeInfo = this.getTypeInfo(Datastore.class, cnsProperties);
      return new ArrayList<TypeInfo>(folderTypeInfo, datacenterTypeInfo, clusterTypeInfo, datastoreTypeInfo) {
         {
            this.add(var2);
            this.add(var3);
            this.add(var4);
            this.add(var5);
         }
      };
   }

   private <T> TypeInfo getTypeInfo(Class<T> typeInfoType, String[] properties) {
      TypeInfo typeInfo = new TypeInfo();
      typeInfo.type = typeInfoType.getSimpleName();
      typeInfo.properties = properties;
      return typeInfo;
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
               List<PropertyValue> propValues = new ArrayList();
               PropertyValue propValue;
               if (QueryUtil.isAnyPropertyRequested(propertyRequest.properties, "isImprovedCapacityMonitoringSupportedOnVc")) {
                  propValue = QueryUtil.newProperty("isImprovedCapacityMonitoringSupportedOnVc", VsanCapabilityUtils.isImprovedCapacityMonitoringSupportedOnVc(moRef));
                  propValue.resourceObject = objectRef;
                  propValues.add(propValue);
               }

               if (QueryUtil.isAnyPropertyRequested(propertyRequest.properties, "isVmLevelCapacityMonitoringSupported")) {
                  propValue = QueryUtil.newProperty("isVmLevelCapacityMonitoringSupported", VsanCapabilityUtils.isVmLevelCapacityMonitoringSupportedOnVc(moRef));
                  propValue.resourceObject = objectRef;
                  propValues.add(propValue);
               }

               PropertyValue propValue;
               boolean remoteSupported;
               if (QueryUtil.isAnyPropertyRequested(propertyRequest.properties, "isVsanArchiveDpSupported")) {
                  remoteSupported = false;
                  if (ClusterComputeResource.class.isAssignableFrom(this.vmodlHelper.getTypeClass(moRef))) {
                     remoteSupported = VsanCapabilityUtils.isArchiveDataProtectionSupported(moRef);
                  } else {
                     remoteSupported = VsanCapabilityUtils.isArchiveDataProtectionSupportedOnVc(moRef);
                  }

                  propValue = QueryUtil.newProperty("isVsanArchiveDpSupported", remoteSupported);
                  propValue.resourceObject = objectRef;
                  propValues.add(propValue);
               }

               if (QueryUtil.isAnyPropertyRequested(propertyRequest.properties, "isVsanRemoteDpSupported")) {
                  remoteSupported = false;
                  if (ClusterComputeResource.class.isAssignableFrom(this.vmodlHelper.getTypeClass(moRef))) {
                     remoteSupported = VsanCapabilityUtils.isRemoteDataProtectionSupported(moRef);
                  } else {
                     remoteSupported = VsanCapabilityUtils.isRemoteDataProtectionSupportedOnVc(moRef);
                  }

                  propValue = QueryUtil.newProperty("isVsanRemoteDpSupported", remoteSupported);
                  propValue.resourceObject = objectRef;
                  propValues.add(propValue);
               }

               if (QueryUtil.isAnyPropertyRequested(propertyRequest.properties, "isCnsVolumesSupportedOnVc")) {
                  propValue = QueryUtil.newProperty("isCnsVolumesSupportedOnVc", VsanCapabilityUtils.isCnsVolumesSupportedOnVc(moRef));
                  propValue.resourceObject = objectRef;
                  propValues.add(propValue);
               }

               if (QueryUtil.isAnyPropertyRequested(propertyRequest.properties, "isVsanNestedFdsSupported")) {
                  propValue = QueryUtil.newProperty("isVsanNestedFdsSupported", VsanCapabilityUtils.isVsanNestedFdsSupportedOnVc((ManagedObjectReference)objectRef));
                  propValue.resourceObject = objectRef;
                  propValues.add(propValue);
               }

               resultItems.add(QueryUtil.newResultItem(objectRef, (PropertyValue[])propValues.toArray(new PropertyValue[0])));
            }
         }

         return QueryUtil.newResultSet((ResultItem[])resultItems.toArray(new ResultItem[0]));
      }
   }
}
