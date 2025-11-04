package com.vmware.vsan.client.services.capability;

import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.HostSystem;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanCapability;
import com.vmware.vim.vsan.binding.vim.cluster.VsanCapabilitySystem;
import com.vmware.vim.vsan.binding.vsan.version.version8;
import com.vmware.vise.usersession.UserSessionService;
import com.vmware.vsphere.client.vsan.base.cache.TimeBasedCacheEntry;
import com.vmware.vsphere.client.vsan.base.cache.TimeBasedCacheManager;
import com.vmware.vsphere.client.vsan.base.data.VsanCapabilityData;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.VersionService;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class VsanCapabilityCacheManager extends TimeBasedCacheManager<ManagedObjectReference, VsanCapabilityData> {
   private static final Log _logger = LogFactory.getLog(VsanCapabilityCacheManager.class);
   private static final VsanProfiler _profiler = new VsanProfiler(VsanCapabilityCacheManager.class);
   private final UserSessionService sessionService;
   @Autowired
   private VcClient vcClient;
   @Autowired
   private VersionService versionService;
   // $FF: synthetic field
   private static int[] $SWITCH_TABLE$com$vmware$vsan$client$services$capability$VsanCapabilityCacheManager$VsanCacheType;

   public VsanCapabilityCacheManager(UserSessionService sessionService, int expirationTimeMin, int expirationTimeMax, int trustPeriod, int cleanThreshold) {
      super(expirationTimeMin, expirationTimeMax, trustPeriod, cleanThreshold);
      this.sessionService = sessionService;
   }

   public VsanCapabilityData getVcCapabilities(ManagedObjectReference ref) {
      this.validateMoRef(ref);
      return (VsanCapabilityData)super.get(ref, VsanCapabilityCacheManager.VsanCacheType.VC);
   }

   public VsanCapabilityData getClusterCapabilities(ManagedObjectReference ref) {
      this.validateMoRefAndType(ref, ClusterComputeResource.class.getSimpleName());
      return (VsanCapabilityData)super.get(ref, VsanCapabilityCacheManager.VsanCacheType.CLUSTER);
   }

   public VsanCapabilityData getHostCapabilities(ManagedObjectReference ref) {
      this.validateMoRefAndType(ref, HostSystem.class.getSimpleName());
      return (VsanCapabilityData)super.get(ref, VsanCapabilityCacheManager.VsanCacheType.HOST);
   }

   protected String getKey(ManagedObjectReference moRef, TimeBasedCacheManager.CacheType type) {
      switch($SWITCH_TABLE$com$vmware$vsan$client$services$capability$VsanCapabilityCacheManager$VsanCacheType()[((VsanCapabilityCacheManager.VsanCacheType)type).ordinal()]) {
      case 1:
         return moRef.getServerGuid();
      case 2:
      case 3:
         return moRef.getServerGuid() + ":" + moRef.getValue();
      default:
         throw new UnsupportedOperationException("Unsupported cache manager type!");
      }
   }

   protected String sessionKey() {
      String key = this.sessionService.getUserSession().clientId;
      if (key == null) {
         throw new RuntimeException("Failed to retrieve the clientId from the session. Most probably, the threadlocal context is not correctly set. Session: " + this.sessionService.getUserSession());
      } else {
         return key;
      }
   }

   protected TimeBasedCacheEntry<VsanCapabilityData> createEntry(ManagedObjectReference moRef, TimeBasedCacheManager.CacheType type) {
      switch($SWITCH_TABLE$com$vmware$vsan$client$services$capability$VsanCapabilityCacheManager$VsanCacheType()[((VsanCapabilityCacheManager.VsanCacheType)type).ordinal()]) {
      case 1:
         return new VsanCapabilityCacheManager.VcCapabilityTimeBasedCacheEntry(moRef);
      case 2:
         return new VsanCapabilityCacheManager.HostCapabilityTimeBasedCacheEntry(moRef);
      case 3:
         return new VsanCapabilityCacheManager.ClusterCapabilityTimeBasedCacheEntry(moRef);
      default:
         throw new UnsupportedOperationException("Unsupported cache manager type!");
      }
   }

   private void validateMoRefAndType(ManagedObjectReference moRef, String expectedType) {
      this.validateMoRef(moRef);
      String moRefType = moRef.getType();
      if (!expectedType.equals(moRefType)) {
         throw new IllegalArgumentException(String.format("Unsupported ManagedObjectReference type: %s. Expected is: %s.", moRefType, expectedType));
      }
   }

   private void validateMoRef(ManagedObjectReference moRef) {
      Validate.notNull(moRef);
      Validate.notEmpty(moRef.getServerGuid());
   }

   // $FF: synthetic method
   static int[] $SWITCH_TABLE$com$vmware$vsan$client$services$capability$VsanCapabilityCacheManager$VsanCacheType() {
      int[] var10000 = $SWITCH_TABLE$com$vmware$vsan$client$services$capability$VsanCapabilityCacheManager$VsanCacheType;
      if (var10000 != null) {
         return var10000;
      } else {
         int[] var0 = new int[VsanCapabilityCacheManager.VsanCacheType.values().length];

         try {
            var0[VsanCapabilityCacheManager.VsanCacheType.CLUSTER.ordinal()] = 3;
         } catch (NoSuchFieldError var3) {
         }

         try {
            var0[VsanCapabilityCacheManager.VsanCacheType.HOST.ordinal()] = 2;
         } catch (NoSuchFieldError var2) {
         }

         try {
            var0[VsanCapabilityCacheManager.VsanCacheType.VC.ordinal()] = 1;
         } catch (NoSuchFieldError var1) {
         }

         $SWITCH_TABLE$com$vmware$vsan$client$services$capability$VsanCapabilityCacheManager$VsanCacheType = var0;
         return var0;
      }
   }

   private class ClusterCapabilityTimeBasedCacheEntry extends VsanCapabilityCacheManager.VsanTimeBasedCacheEntry {
      public ClusterCapabilityTimeBasedCacheEntry(ManagedObjectReference moRef) {
         super(moRef);
      }

      protected VsanCapabilityData load() {
         VsanCapabilityData result = new VsanCapabilityData();

         try {
            Throwable var2 = null;
            Object var3 = null;

            try {
               VsanProfiler.Point point = VsanCapabilityCacheManager._profiler.point("VsanCapabilitySystem.getCapabilities");

               try {
                  VsanCapabilitySystem capSystem = VsanProviderUtils.getVsanCapabilitySystem(this._moRef);
                  ManagedObjectReference[] args = this.getArgs();
                  VsanCapability[] capabilities = capSystem.getCapabilities(args);
                  if (!ArrayUtils.isEmpty(capabilities)) {
                     result = VsanCapabilityData.fromVsanCapability(capabilities[0]);
                     if (capabilities.length > 1) {
                        for(int i = 1; i < capabilities.length; ++i) {
                           VsanCapability capability = capabilities[i];
                           VsanCapabilityData hostCapabilities = VsanCapabilityData.fromVsanCapability(capability);
                           result.isCapabilitiesSupported &= hostCapabilities.isDisconnected || hostCapabilities.isCapabilitiesSupported;
                           result.isAllFlashSupported &= hostCapabilities.isDisconnected || hostCapabilities.isAllFlashSupported;
                           result.isStretchedClusterSupported &= hostCapabilities.isDisconnected || hostCapabilities.isStretchedClusterSupported;
                           result.isClusterConfigSupported &= hostCapabilities.isDisconnected || hostCapabilities.isClusterConfigSupported;
                           result.isDeduplicationAndCompressionSupported &= hostCapabilities.isDisconnected || hostCapabilities.isDeduplicationAndCompressionSupported;
                           result.isUpgradeSupported &= hostCapabilities.isDisconnected || hostCapabilities.isUpgradeSupported;
                           result.isObjectIdentitiesSupported &= hostCapabilities.isDisconnected || hostCapabilities.isObjectIdentitiesSupported;
                           result.isIscsiTargetsSupported &= hostCapabilities.isDisconnected || hostCapabilities.isIscsiTargetsSupported;
                           result.isWitnessManagementSupported &= hostCapabilities.isDisconnected || hostCapabilities.isWitnessManagementSupported;
                           result.isPerfVerboseModeSupported &= hostCapabilities.isDisconnected || hostCapabilities.isPerfVerboseModeSupported;
                           result.isPerfSvcAutoConfigSupported &= hostCapabilities.isDisconnected || hostCapabilities.isPerfSvcAutoConfigSupported;
                           result.isConfigAssistSupported &= hostCapabilities.isDisconnected || hostCapabilities.isConfigAssistSupported;
                           result.isUpdatesMgmtSupported &= hostCapabilities.isDisconnected || hostCapabilities.isUpdatesMgmtSupported;
                           result.isWhatIfComplianceSupported &= hostCapabilities.isDisconnected || hostCapabilities.isWhatIfComplianceSupported;
                           result.isPerfAnalysisSupported &= hostCapabilities.isDisconnected || hostCapabilities.isPerfAnalysisSupported;
                           result.isResyncThrottlingSupported &= hostCapabilities.isDisconnected || hostCapabilities.isResyncThrottlingSupported;
                           result.isEncryptionSupported &= hostCapabilities.isDisconnected || hostCapabilities.isEncryptionSupported;
                           result.isLocalDataProtectionSupported &= hostCapabilities.isDisconnected || hostCapabilities.isLocalDataProtectionSupported;
                           result.isArchiveDataProtectionSupported &= hostCapabilities.isDisconnected || hostCapabilities.isArchiveDataProtectionSupported;
                           result.isRemoteDataProtectionSupported &= hostCapabilities.isDisconnected || hostCapabilities.isRemoteDataProtectionSupported;
                           result.isVsanVumIntegrationSupported &= hostCapabilities.isDisconnected || hostCapabilities.isVsanVumIntegrationSupported;
                           result.isRepairTimerInResyncStatsSupported &= hostCapabilities.isDisconnected || hostCapabilities.isRepairTimerInResyncStatsSupported;
                           result.isFileServiceSupported &= hostCapabilities.isDisconnected || hostCapabilities.isFileServiceSupported;
                           result.isRdmaSupported &= hostCapabilities.isDisconnected || hostCapabilities.isRdmaSupported;
                           result.isResyncETAImprovementSupported &= hostCapabilities.isDisconnected || hostCapabilities.isResyncETAImprovementSupported;
                           result.isGuestTrimUnmapSupported &= hostCapabilities.isDisconnected || hostCapabilities.isGuestTrimUnmapSupported;
                           result.isVitOnlineResizeSupported &= hostCapabilities.isDisconnected || hostCapabilities.isVitOnlineResizeSupported;
                           result.isResourcePrecheckSupported &= hostCapabilities.isDisconnected || hostCapabilities.isResourcePrecheckSupported;
                        }
                     }
                  }
               } finally {
                  if (point != null) {
                     point.close();
                  }

               }
            } catch (Throwable var18) {
               if (var2 == null) {
                  var2 = var18;
               } else if (var2 != var18) {
                  var2.addSuppressed(var18);
               }

               throw var2;
            }
         } catch (Exception var19) {
            VsanCapabilityCacheManager._logger.error("Cannot retrieve capabilities", var19);
         }

         return result;
      }

      protected String getValidationToken() {
         Throwable var1 = null;
         Object var2 = null;

         try {
            VcConnection vcConnection = VsanCapabilityCacheManager.this.vcClient.getConnection(this._moRef.getServerGuid());

            Throwable var10000;
            label205: {
               boolean var10001;
               String var22;
               try {
                  ClusterComputeResource cluster = (ClusterComputeResource)vcConnection.createStub(ClusterComputeResource.class, this._moRef);
                  Set<ManagedObjectReference> hosts = new HashSet();
                  ManagedObjectReference[] var9;
                  int var8 = (var9 = cluster.getHost()).length;
                  int var7 = 0;

                  while(true) {
                     if (var7 >= var8) {
                        var22 = String.valueOf(hosts.hashCode());
                        break;
                     }

                     ManagedObjectReference hostRef = var9[var7];
                     hosts.add(hostRef);
                     ++var7;
                  }
               } catch (Throwable var20) {
                  var10000 = var20;
                  var10001 = false;
                  break label205;
               }

               if (vcConnection != null) {
                  vcConnection.close();
               }

               label194:
               try {
                  return var22;
               } catch (Throwable var19) {
                  var10000 = var19;
                  var10001 = false;
                  break label194;
               }
            }

            var1 = var10000;
            if (vcConnection != null) {
               vcConnection.close();
            }

            throw var1;
         } catch (Throwable var21) {
            if (var1 == null) {
               var1 = var21;
            } else if (var1 != var21) {
               var1.addSuppressed(var21);
            }

            throw var1;
         }
      }

      protected ManagedObjectReference[] getArgs() {
         ManagedObjectReference clonedMoRef = new ManagedObjectReference(this._moRef.getType(), this._moRef.getValue());
         return new ManagedObjectReference[]{clonedMoRef};
      }
   }

   private class HostCapabilityTimeBasedCacheEntry extends VsanCapabilityCacheManager.VsanTimeBasedCacheEntry {
      public HostCapabilityTimeBasedCacheEntry(ManagedObjectReference moRef) {
         super(moRef);
      }

      protected ManagedObjectReference[] getArgs() {
         ManagedObjectReference clonedMoRef = new ManagedObjectReference(this._moRef.getType(), this._moRef.getValue());
         return new ManagedObjectReference[]{clonedMoRef};
      }

      protected String getValidationToken() {
         Throwable var1 = null;
         Object var2 = null;

         try {
            VcConnection vcConnection = VsanCapabilityCacheManager.this.vcClient.getConnection(this._moRef.getServerGuid());

            Throwable var10000;
            label173: {
               String var17;
               boolean var10001;
               try {
                  HostSystem host = (HostSystem)vcConnection.createStub(HostSystem.class, this._moRef);
                  var17 = host.getRuntime().getConnectionState().name();
               } catch (Throwable var15) {
                  var10000 = var15;
                  var10001 = false;
                  break label173;
               }

               if (vcConnection != null) {
                  vcConnection.close();
               }

               label162:
               try {
                  return var17;
               } catch (Throwable var14) {
                  var10000 = var14;
                  var10001 = false;
                  break label162;
               }
            }

            var1 = var10000;
            if (vcConnection != null) {
               vcConnection.close();
            }

            throw var1;
         } catch (Throwable var16) {
            if (var1 == null) {
               var1 = var16;
            } else if (var1 != var16) {
               var1.addSuppressed(var16);
            }

            throw var1;
         }
      }
   }

   private class VcCapabilityTimeBasedCacheEntry extends VsanCapabilityCacheManager.VsanTimeBasedCacheEntry {
      public VcCapabilityTimeBasedCacheEntry(ManagedObjectReference moRef) {
         super(moRef);
      }

      protected VsanCapabilityData load() {
         VsanCapabilityData result = super.load();
         result.isUnmountWithMaintenanceModeSupported = VsanCapabilityCacheManager.this.versionService.isVsanVmodlVersionHigherThan(this._moRef, version8.class);
         return result;
      }

      protected ManagedObjectReference[] getArgs() {
         return new ManagedObjectReference[0];
      }
   }

   private static enum VsanCacheType implements TimeBasedCacheManager.CacheType {
      VC,
      HOST,
      CLUSTER;
   }

   private abstract class VsanTimeBasedCacheEntry extends TimeBasedCacheEntry<VsanCapabilityData> {
      protected final ManagedObjectReference _moRef;

      public VsanTimeBasedCacheEntry(ManagedObjectReference moRef) {
         this._moRef = moRef;
      }

      protected VsanCapabilityData load() {
         VsanCapabilityData result = new VsanCapabilityData();

         try {
            Throwable var2 = null;
            Object var3 = null;

            try {
               VsanProfiler.Point point = VsanCapabilityCacheManager._profiler.point("VsanCapabilitySystem.getCapabilities");

               try {
                  VsanCapabilitySystem capSystem = VsanProviderUtils.getVsanCapabilitySystem(this._moRef);
                  ManagedObjectReference[] args = this.getArgs();
                  VsanCapability[] capabilities = capSystem.getCapabilities(args);
                  if (!ArrayUtils.isEmpty(capabilities)) {
                     result = VsanCapabilityData.fromVsanCapability(capabilities[0]);
                  }
               } finally {
                  if (point != null) {
                     point.close();
                  }

               }
            } catch (Throwable var15) {
               if (var2 == null) {
                  var2 = var15;
               } else if (var2 != var15) {
                  var2.addSuppressed(var15);
               }

               throw var2;
            }
         } catch (Exception var16) {
            VsanCapabilityCacheManager._logger.error("Cannot retrieve capabilities", var16);
         }

         return result;
      }

      protected String getValidationToken() {
         return null;
      }

      protected abstract ManagedObjectReference[] getArgs();
   }
}
