package com.vmware.vsan.client.services.resyncing;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vsphere.client.vsan.base.data.IscsiLun;
import com.vmware.vsphere.client.vsan.base.data.IscsiTarget;
import com.vmware.vsphere.client.vsan.base.data.VsanObject;
import com.vmware.vsphere.client.vsan.iscsi.models.VsanIscsiTargetProviderParameter;
import com.vmware.vsphere.client.vsan.iscsi.providers.VsanIscsiTargetPropertyProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VsanResyncingIscsiTargetComponentsProvider {
   private static final String ISCSI_ROOT_VALUE = "iSCSI Objects";
   private static final Log _logger = LogFactory.getLog(VsanResyncingIscsiTargetComponentsProvider.class);
   @Autowired
   private VsanIscsiTargetPropertyProvider iscsiTargetPropertyProvider;

   public Map<String, VsanObject> getIscsiResyncObjects(ManagedObjectReference clusterRef, Set<String> resyncObjectsUuids) {
      VsanIscsiTargetProviderParameter iscsiTargetsParam = new VsanIscsiTargetProviderParameter(false, false);

      try {
         IscsiTarget[] iscsiTargets = this.iscsiTargetPropertyProvider.getIscsiTargets(clusterRef, iscsiTargetsParam);
         return this.getResyncingIscsiTargets(iscsiTargets, resyncObjectsUuids);
      } catch (Exception var5) {
         _logger.error("Unable to fetch the iscsi targets from cluster " + clusterRef, var5);
         return null;
      }
   }

   private Map<String, VsanObject> getResyncingIscsiTargets(IscsiTarget[] iscsiTargets, Set<String> resyncObjectsUuids) throws Exception {
      Map<String, VsanObject> result = new HashMap();
      IscsiTarget[] var7 = iscsiTargets;
      int var6 = iscsiTargets.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         IscsiTarget iscsiTarget = var7[var5];
         if (resyncObjectsUuids.contains(iscsiTarget.vsanObjectUuid)) {
            result.put(iscsiTarget.vsanObjectUuid, iscsiTarget);
         }

         if (iscsiTarget.lunCount != 0) {
            for(int i = iscsiTarget.luns.size(); i > 0; --i) {
               IscsiLun lun = (IscsiLun)iscsiTarget.luns.get(i - 1);
               if (resyncObjectsUuids.contains(lun.vsanObjectUuid)) {
                  result.put(iscsiTarget.vsanObjectUuid, iscsiTarget);
                  result.put(lun.vsanObjectUuid, lun);
               } else {
                  iscsiTarget.luns.remove(i - 1);
               }
            }
         }
      }

      return result;
   }
}
