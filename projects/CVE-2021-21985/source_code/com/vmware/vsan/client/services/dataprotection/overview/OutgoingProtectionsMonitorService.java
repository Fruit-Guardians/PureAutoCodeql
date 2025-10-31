package com.vmware.vsan.client.services.dataprotection.overview;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectIdentity;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectIdentityAndHealth;
import com.vmware.vim.vsandp.binding.vim.vsandp.StorageObjectInfo;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService.TargetFilterSpec;
import com.vmware.vsan.client.services.dataprotection.ProtectionsMonitorService;
import com.vmware.vsan.client.services.dataprotection.model.ProtectionItem;
import com.vmware.vsan.client.services.dataprotection.model.ProtectionsMonitorData;
import com.vmware.vsan.client.services.dataprotection.overview.model.OutgoingProtectionItemData;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsan.client.util.retriever.VsanAsyncDataRetriever;
import com.vmware.vsan.client.util.retriever.VsanDataRetrieverFactory;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OutgoingProtectionsMonitorService extends ProtectionsMonitorService {
   private static final Log logger = LogFactory.getLog(OutgoingProtectionsMonitorService.class);
   private static final String NAMESPACE_IDENTITY_OBJECT_CLASS = "vmnamespace";
   @Autowired
   private VsanDataRetrieverFactory dataRetrieverFactory;

   private String findNamespaceIdentityKey(StorageObjectInfo[] infos) {
      StorageObjectInfo[] var5 = infos;
      int var4 = infos.length;

      for(int var3 = 0; var3 < var4; ++var3) {
         StorageObjectInfo info = var5[var3];
         if (info.objClass.equals("vmnamespace")) {
            return info.key;
         }
      }

      return null;
   }

   protected TargetFilterSpec buildProtectionFilter() {
      TargetFilterSpec result = super.buildProtectionFilter();
      result.setLocalRequested(true);
      result.setArchiveRequested(true);
      result.setRemoteRequested(true);
      return result;
   }

   @TsService
   public ProtectionsMonitorData getProtecionsData(ManagedObjectReference clusterRef, String sourceDsUrl) {
      ProtectionsMonitorData data = this.getProtectionsData(clusterRef, sourceDsUrl);
      this.populateAdditionalInfo(clusterRef, data.protectionItems);
      return data;
   }

   private void populateAdditionalInfo(ManagedObjectReference clusterRef, Collection<ProtectionItem> items) {
      Map<String, OutgoingProtectionItemData> namespaceKeyToItem = new HashMap();
      Iterator var5 = items.iterator();

      while(var5.hasNext()) {
         ProtectionItem iter = (ProtectionItem)var5.next();
         OutgoingProtectionItemData item = (OutgoingProtectionItemData)iter;
         if (item.namespaceIdentityKey != null) {
            namespaceKeyToItem.put(item.namespaceIdentityKey, item);
         }
      }

      try {
         Throwable var23 = null;
         var5 = null;

         try {
            Measure measure = new Measure("Collect info for protected VM Objects for cluster");

            try {
               VsanAsyncDataRetriever dataRetriever = this.dataRetrieverFactory.createVsanAsyncDataRetriever(measure, clusterRef).loadObjectIdentities(namespaceKeyToItem.keySet()).loadStoragePolicies();
               VsanObjectIdentityAndHealth identities = dataRetriever.getObjectIdentities();
               Map<String, String> policyNamesById = dataRetriever.getStoragePolicies();
               Iterator var11 = namespaceKeyToItem.keySet().iterator();

               while(var11.hasNext()) {
                  String namespaceKey = (String)var11.next();
                  VsanObjectIdentity identity = this.findIdentityByKey(namespaceKey, identities.identities);
                  if (identity != null) {
                     OutgoingProtectionItemData item = (OutgoingProtectionItemData)namespaceKeyToItem.get(namespaceKey);
                     item.moRef = identity.vm;
                     item.policyId = identity.getSpbmProfileUuid();
                     if (identity.getSpbmProfileUuid() != null) {
                        item.policyName = (String)policyNamesById.get(identity.getSpbmProfileUuid());
                        if (item.policyName == null) {
                           item.policyName = item.policyId;
                        }
                     }
                  } else {
                     logger.error("Unable to find identity for key " + namespaceKey + ". Expected missing info about storage policy.");
                  }
               }
            } finally {
               if (measure != null) {
                  measure.close();
               }

            }
         } catch (Throwable var21) {
            if (var23 == null) {
               var23 = var21;
            } else if (var23 != var21) {
               var23.addSuppressed(var21);
            }

            throw var23;
         }
      } catch (ExecutionException | InterruptedException var22) {
         logger.error("Unable to retrieve additional info for replications. Expected missing info about storage policy.", var22);
      }

   }

   private VsanObjectIdentity findIdentityByKey(String storageKey, VsanObjectIdentity[] identities) {
      if (identities == null) {
         return null;
      } else {
         VsanObjectIdentity[] var6 = identities;
         int var5 = identities.length;

         for(int var4 = 0; var4 < var5; ++var4) {
            VsanObjectIdentity identity = var6[var4];
            if (identity.getUuid().equals(storageKey)) {
               return identity;
            }
         }

         return null;
      }
   }
}
