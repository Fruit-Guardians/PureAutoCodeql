package com.vmware.vsan.client.services.dataprotection;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsandp.binding.vim.vsandp.DatastoreInfo;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService.TargetFilterSpec;
import com.vmware.vsan.client.services.capability.VsanCapabilityProvider;
import com.vmware.vsan.client.services.dataprotection.model.ProtectionsMonitorData;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.DpClient;
import com.vmware.vsphere.client.vsandp.helper.VsanDpInventoryHelper;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class ProtectionsMonitorService {
   private static final Log logger = LogFactory.getLog(ProtectionsMonitorService.class);
   private static String LAST_INSTANCE_TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss";
   @Autowired
   protected DpClient dpClient;
   @Autowired
   protected VsanDpInventoryHelper inventoryHelper;
   @Autowired
   protected VsanCapabilityProvider capabilityProvider;

   protected ProtectionsMonitorData getProtectionsData(ManagedObjectReference clusterRef, String sourceDsUrl) {
      ProtectionsMonitorData result = new ProtectionsMonitorData();
      return result;
   }

   protected Date formatDate(String dateStr) {
      if (dateStr == null) {
         return null;
      } else {
         try {
            return (new SimpleDateFormat(LAST_INSTANCE_TIMESTAMP_FORMAT)).parse(dateStr);
         } catch (ParseException var3) {
            logger.error("Can not parse date from " + dateStr, var3);
            return null;
         }
      }
   }

   protected TargetFilterSpec buildProtectionFilter() {
      TargetFilterSpec result = new TargetFilterSpec();
      return result;
   }

   private DatastoreInfo buildDatastoreInfo(ManagedObjectReference clusterRef, String datastoreUrl) throws Exception {
      DatastoreInfo result = new DatastoreInfo();
      result.setDatastore(this.inventoryHelper.getVsanDatastore(clusterRef, datastoreUrl));
      return result;
   }
}
