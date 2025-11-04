package com.vmware.vsphere.client.vsan.upgrade;

import com.vmware.vim.binding.vim.HostSystem;
import com.vmware.vim.binding.vim.host.ScsiDisk;
import com.vmware.vim.binding.vim.vsan.host.DiskMapping;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.data.query.DataServiceExtensionRegistry;
import com.vmware.vise.data.query.PropertyProviderAdapter;
import com.vmware.vise.data.query.PropertyRequestSpec;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.ResultItem;
import com.vmware.vise.data.query.ResultSet;
import com.vmware.vise.data.query.TypeInfo;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class VsanUpgradePropertyProviderAdapter implements PropertyProviderAdapter {
   private static final String VSAN_DISK_VERSION_PROPERTY_NAME = "vsanDiskVersionsData";
   private static final String DISK_MAPPINGS = "config.vsanHostConfig.storageInfo.diskMapping";
   private static final Log _logger = LogFactory.getLog(VsanUpgradePropertyProviderAdapter.class);

   public VsanUpgradePropertyProviderAdapter(DataServiceExtensionRegistry registry) {
      Validate.notNull(registry);
      TypeInfo hostInfo = new TypeInfo();
      hostInfo.type = HostSystem.class.getSimpleName();
      hostInfo.properties = new String[]{"vsanDiskVersionsData"};
      TypeInfo[] providedProperties = new TypeInfo[]{hostInfo};
      registry.registerDataAdapter(this, providedProperties);
   }

   public ResultSet getProperties(PropertyRequestSpec propertyRequest) {
      ResultSet resultSet;
      if (!this.isValidRequest(propertyRequest)) {
         resultSet = new ResultSet();
         resultSet.totalMatchedObjectCount = 0;
         return resultSet;
      } else {
         resultSet = null;
         ArrayList resultItems = new ArrayList();

         try {
            ManagedObjectReference[] moRefs = (ManagedObjectReference[])Arrays.copyOf(propertyRequest.objects, propertyRequest.objects.length, ManagedObjectReference[].class);
            PropertyValue[] propValues = QueryUtil.getProperties(moRefs, new String[]{"config.vsanHostConfig.storageInfo.diskMapping"}).getPropertyValues();
            PropertyValue[] var9 = propValues;
            int var8 = propValues.length;

            for(int var7 = 0; var7 < var8; ++var7) {
               PropertyValue propValue = var9[var7];
               DiskMapping[] diskMappings = (DiskMapping[])propValue.value;
               ManagedObjectReference hostRef = (ManagedObjectReference)propValue.resourceObject;
               VsanDiskVersionData[] hostDiskVersionData = this.getHostDiskVersionsData(hostRef, diskMappings);
               PropertyValue resultPropValue = QueryUtil.newProperty("vsanDiskVersionsData", hostDiskVersionData);
               resultPropValue.resourceObject = hostRef;
               ResultItem resultItem = QueryUtil.newResultItem(hostRef, resultPropValue);
               resultItems.add(resultItem);
            }
         } catch (Exception var15) {
            _logger.error("Failed to retrieve properties from DS. ", var15);
            resultSet = new ResultSet();
            resultSet.error = var15;
            return resultSet;
         }

         resultSet = QueryUtil.newResultSet((ResultItem[])resultItems.toArray(new ResultItem[resultItems.size()]));
         return resultSet;
      }
   }

   private VsanDiskVersionData getDiskVersionData(ScsiDisk scsiDisk) {
      return scsiDisk.vsanDiskInfo == null ? new VsanDiskVersionData() : new VsanDiskVersionData(scsiDisk.vsanDiskInfo);
   }

   private VsanDiskVersionData[] getHostDiskVersionsData(ManagedObjectReference host, DiskMapping[] diskGroups) throws Exception {
      if (ArrayUtils.isEmpty(diskGroups)) {
         return null;
      } else {
         List<VsanDiskVersionData> disksData = new ArrayList();
         DiskMapping[] var7 = diskGroups;
         int var6 = diskGroups.length;

         for(int var5 = 0; var5 < var6; ++var5) {
            DiskMapping diskGroup = var7[var5];
            disksData.add(this.getDiskVersionData(diskGroup.ssd));
            ScsiDisk[] var11;
            int var10 = (var11 = diskGroup.nonSsd).length;

            for(int var9 = 0; var9 < var10; ++var9) {
               ScsiDisk disk = var11[var9];
               disksData.add(this.getDiskVersionData(disk));
            }
         }

         return (VsanDiskVersionData[])disksData.toArray(new VsanDiskVersionData[disksData.size()]);
      }
   }

   private boolean isValidRequest(PropertyRequestSpec propertyRequest) {
      if (propertyRequest == null) {
         return false;
      } else if (!ArrayUtils.isEmpty(propertyRequest.objects) && !ArrayUtils.isEmpty(propertyRequest.properties)) {
         if (!(propertyRequest.objects[0] instanceof ManagedObjectReference)) {
            _logger.error("VsanUpgradePropertyProviderAdapter got a list of objects that are not of type ManagedObjectReferences");
            return false;
         } else {
            return true;
         }
      } else {
         _logger.error("VsanUpgradePropertyProviderAdapter got a null or empty list of properties or objects");
         return false;
      }
   }
}
