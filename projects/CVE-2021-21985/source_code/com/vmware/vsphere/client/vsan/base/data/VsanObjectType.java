package com.vmware.vsphere.client.vsan.base.data;

import com.vmware.vise.core.model.data;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

@data
public enum VsanObjectType {
   vmswap,
   vdisk,
   namespace,
   vmem,
   statsdb,
   iscsiTarget,
   iscsiLun,
   other,
   fileSystemOverhead,
   dedupOverhead,
   spaceUnderDedupConsideration,
   checksumOverhead,
   improvedVirtualDisk,
   transientSpace,
   fileShare,
   attachedCnsVolBlock,
   detachedCnsVolBlock,
   attachedCnsVolFile,
   detachedCnsVolFile,
   vdiskSnapshot,
   dpConsistencyGroup;

   private static final Log _logger = LogFactory.getLog(VsanObjectType.class);

   public static VsanObjectType parse(String value) {
      try {
         return valueOf(value);
      } catch (Exception var2) {
         _logger.warn("Unknown vSAN Object Type: " + value, var2);
         return other;
      }
   }
}
