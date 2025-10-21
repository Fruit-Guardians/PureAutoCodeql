package com.vmware.vsphere.client.vsan.base.util;

import com.vmware.vim.binding.pbm.capability.CapabilityInstance;
import com.vmware.vim.binding.pbm.capability.ConstraintInstance;
import com.vmware.vim.binding.pbm.capability.Operator;
import com.vmware.vim.binding.pbm.capability.PropertyInstance;
import com.vmware.vim.binding.pbm.capability.CapabilityMetadata.UniqueId;
import com.vmware.vim.binding.pbm.compliance.ComplianceResult;
import com.vmware.vim.binding.pbm.compliance.PolicyStatus;
import com.vmware.vim.binding.pbm.compliance.ComplianceResult.ComplianceStatus;
import com.vmware.vim.binding.pbm.profile.ProfileId;
import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.Datastore;
import com.vmware.vim.binding.vim.VirtualMachine;
import com.vmware.vim.binding.vim.Datastore.HostMount;
import com.vmware.vim.binding.vim.host.DiskDimensions.Lba;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.StorageComplianceResult;
import com.vmware.vim.vsan.binding.vim.cluster.StorageOperationalStatus;
import com.vmware.vim.vsan.binding.vim.cluster.StoragePolicyStatus;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.base.data.VsanComplianceStatus;
import com.vmware.vsphere.client.vsan.base.data.VsanOperationalStatus;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class BaseUtils {
   private static final int BLOCK_SIZE_DEFAULT = 512;
   public static final String VMWARE_VSAN_NAMESPACE = "VSAN";
   private static final String DATASTORE_HOST_PROPERTY = "host";
   private static final String HOST_PARENT_PROPERTY = "parent";
   private static final Log _logger = LogFactory.getLog(BaseUtils.class);

   public static void setUTCTimeZone(Calendar calendar) {
      if (calendar != null) {
         calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
      }

   }

   public static Map<String, Object> getProperties(ManagedObjectReference moRef, String[] properties) throws Exception {
      HashMap<String, Object> result = new HashMap();
      PropertyValue[] propValues = QueryUtil.getProperties(moRef, properties).getPropertyValues();
      if (propValues != null) {
         PropertyValue[] var7 = propValues;
         int var6 = propValues.length;

         for(int var5 = 0; var5 < var6; ++var5) {
            PropertyValue propValue = var7[var5];
            result.put(propValue.propertyName, propValue.value);
         }
      }

      return result;
   }

   public static long lbaToBytes(Lba lba) {
      int blockSize = lba.blockSize;
      if (blockSize == 0) {
         blockSize = 512;
      }

      return lba.block * (long)blockSize;
   }

   public static VsanComplianceStatus getComplianceStatus(ComplianceResult complianceResult) {
      if (complianceResult == null) {
         return VsanComplianceStatus.UNKNOWN;
      } else if (complianceResult.mismatch) {
         return VsanComplianceStatus.OUT_OF_DATE;
      } else if (complianceResult.complianceStatus.equals(ComplianceStatus.compliant.name())) {
         return VsanComplianceStatus.COMPLIANT;
      } else if (complianceResult.complianceStatus.equals(ComplianceStatus.nonCompliant.name())) {
         return VsanComplianceStatus.NOT_COMPLIANT;
      } else {
         return complianceResult.complianceStatus.equals(ComplianceStatus.notApplicable.name()) ? VsanComplianceStatus.NOT_APPLICABLE : VsanComplianceStatus.UNKNOWN;
      }
   }

   public static VsanOperationalStatus getOperationalState(StorageOperationalStatus operationalStatus) {
      if (operationalStatus == null) {
         return null;
      } else if (operationalStatus.healthy != null && operationalStatus.healthy) {
         return operationalStatus.transitional != null && operationalStatus.transitional ? VsanOperationalStatus.HEALTHY_TRANSITIONAL : VsanOperationalStatus.HEALTHY;
      } else {
         return operationalStatus.transitional != null && operationalStatus.transitional ? VsanOperationalStatus.UNHEALTHY_TRANSITIONAL : VsanOperationalStatus.UNHEALTHY_DISK_UNAVAILABLE;
      }
   }

   public static ComplianceResult toComplianceResult(StorageComplianceResult storageComplianceResult) {
      if (storageComplianceResult == null) {
         return null;
      } else {
         ComplianceResult result = new ComplianceResult();
         result.checkTime = storageComplianceResult.checkTime;
         result.mismatch = storageComplianceResult.mismatch;
         List<PolicyStatus> violatedPolicies = new ArrayList();
         if (storageComplianceResult.violatedPolicies != null) {
            StoragePolicyStatus[] var6;
            int var5 = (var6 = storageComplianceResult.violatedPolicies).length;

            for(int var4 = 0; var4 < var5; ++var4) {
               StoragePolicyStatus status = var6[var4];
               String id = status.id == null ? "" : status.id;
               CapabilityInstance expInstance = new CapabilityInstance();
               expInstance.id = new UniqueId("VSAN", id);
               expInstance.constraint = new ConstraintInstance[]{new ConstraintInstance(new PropertyInstance[]{new PropertyInstance(status.id, Operator.NOT.toString(), status.expectedValue)})};
               CapabilityInstance currInstance = new CapabilityInstance();
               currInstance.id = new UniqueId("VSAN", id);
               currInstance.constraint = new ConstraintInstance[]{new ConstraintInstance(new PropertyInstance[]{new PropertyInstance(status.id, Operator.NOT.toString(), status.currentValue)})};
               PolicyStatus newStatus = new PolicyStatus(expInstance, currInstance);
               violatedPolicies.add(newStatus);
            }
         }

         result.violatedPolicies = (PolicyStatus[])violatedPolicies.toArray(new PolicyStatus[violatedPolicies.size()]);
         result.complianceStatus = storageComplianceResult.complianceStatus;
         result.profile = new ProfileId(storageComplianceResult.profile);
         return result;
      }
   }

   public static ManagedObjectReference getCluster(ManagedObjectReference moRef) {
      Validate.notNull(moRef);
      String moRefType = moRef.getType();
      if (ClusterComputeResource.class.getSimpleName().equalsIgnoreCase(moRefType)) {
         return moRef;
      } else if (Datastore.class.getSimpleName().equalsIgnoreCase(moRefType)) {
         return getClusterFromDatastore(moRef);
      } else if (VirtualMachine.class.getSimpleName().equalsIgnoreCase(moRefType)) {
         return getVmCluster(moRef);
      } else {
         throw new IllegalArgumentException("Not supported MoRef type.");
      }
   }

   private static ManagedObjectReference getClusterFromDatastore(ManagedObjectReference dsRef) {
      try {
         HostMount[] hosts = (HostMount[])QueryUtil.getProperty(dsRef, "host");
         return getParentCluster(hosts);
      } catch (Exception var2) {
         _logger.error("Could not retrieve cluster for datastore: " + dsRef, var2);
         return null;
      }
   }

   private static ManagedObjectReference getParentCluster(HostMount[] hostMounts) throws Exception {
      List<ManagedObjectReference> hosts = new ArrayList();
      HostMount[] var5 = hostMounts;
      int var4 = hostMounts.length;

      for(int var3 = 0; var3 < var4; ++var3) {
         HostMount h = var5[var3];
         if (h.key != null) {
            hosts.add(h.key);
         }
      }

      PropertyValue[] propValues = QueryUtil.getProperties((ManagedObjectReference[])hosts.toArray(new ManagedObjectReference[0]), new String[]{"parent"}).getPropertyValues();
      PropertyValue[] var6 = propValues;
      int var9 = propValues.length;

      for(var4 = 0; var4 < var9; ++var4) {
         PropertyValue propValue = var6[var4];
         if (propValue.value != null && propValue.value instanceof ManagedObjectReference) {
            return (ManagedObjectReference)propValue.value;
         }
      }

      return null;
   }

   private static ManagedObjectReference getVmCluster(ManagedObjectReference vmRef) {
      try {
         ManagedObjectReference clusterRef = (ManagedObjectReference)QueryUtil.getProperty(vmRef, "cluster");
         return VmodlHelper.assignServerGuid(clusterRef, vmRef.getServerGuid());
      } catch (Exception var2) {
         return null;
      }
   }

   public static ManagedObjectReference generateMor(String rowValue, String serverGuid) {
      String[] params = rowValue.split(":");
      return params != null && params.length >= 3 ? new ManagedObjectReference(params[params.length - 2], params[params.length - 1], serverGuid) : null;
   }
}
