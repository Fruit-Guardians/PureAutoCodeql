package com.vmware.vsphere.client.vsandp.controllers.vm.monitor.vsan.provider.pits.impl;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsandp.binding.vim.vsandp.ArchivalProtectionInfo;
import com.vmware.vim.vsandp.binding.vim.vsandp.CgInfo;
import com.vmware.vim.vsandp.binding.vim.vsandp.GroupInstanceData;
import com.vmware.vim.vsandp.binding.vim.vsandp.LocalProtectionInfo;
import com.vmware.vim.vsandp.binding.vim.vsandp.RemoteProtectionInfo;
import com.vmware.vim.vsandp.binding.vim.vsandp.TargetGroupInstanceData;
import com.vmware.vim.vsandp.binding.vim.vsandp.TargetProtectionInfo;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService.InstanceQuery.Result.SeriesEntry;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsphere.client.vsandp.controllers.vm.monitor.vsan.DataProtectionSyncPointsService;
import com.vmware.vsphere.client.vsandp.controllers.vm.monitor.vsan.provider.pits.PitProvider;
import com.vmware.vsphere.client.vsandp.data.DataProtectionInstance;
import com.vmware.vsphere.client.vsandp.data.ProtectionType;
import com.vmware.vsphere.client.vsandp.dataproviders.vm.VmConsistencyGroupPropertyProvider;
import com.vmware.vsphere.client.vsandp.helper.VsanDpInventoryHelper;
import java.util.TreeSet;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VsanDpServicePitProvider implements PitProvider {
   private static final Logger logger = LoggerFactory.getLogger(VsanDpServicePitProvider.class);
   @Autowired
   private VmConsistencyGroupPropertyProvider cgProvider;
   @Autowired
   private VsanDpInventoryHelper inventoryHelper;

   public TreeSet<DataProtectionInstance> getLocalPits(ManagedObjectReference vmRef, CgInfo vmCgInfo) {
      ManagedObjectReference clusterRef = this.inventoryHelper.getVmCluster(vmRef);
      if (clusterRef != null && VsanCapabilityUtils.isLocalDataProtectionSupported(clusterRef)) {
         if (vmCgInfo != null && vmCgInfo.getLocal() != null) {
            LocalProtectionInfo localProtection = vmCgInfo.getLocal();
            TreeSet<DataProtectionInstance> localPits = new TreeSet(new DataProtectionSyncPointsService.DataProtectionInstanceComparator());
            if (localProtection.getInstance() == null) {
               logger.debug("No local protection PITs for vm " + vmRef.getValue());
               return localPits;
            } else {
               GroupInstanceData[] var9;
               int var8 = (var9 = localProtection.getInstance()).length;

               for(int var7 = 0; var7 < var8; ++var7) {
                  GroupInstanceData instance = var9[var7];
                  localPits.add(DataProtectionInstance.createInstance(localProtection.series.key, ProtectionType.LOCAL, instance, vmRef, vmCgInfo.key));
               }

               return localPits;
            }
         } else {
            logger.info("No local protection info for vm " + vmRef.getValue());
            return null;
         }
      } else {
         return null;
      }
   }

   public TreeSet<DataProtectionInstance> getArchivePits(ManagedObjectReference vmRef, CgInfo vmCgInfo) {
      ManagedObjectReference clusterRef = this.inventoryHelper.getVmCluster(vmRef);
      if (clusterRef != null && VsanCapabilityUtils.isArchiveDataProtectionSupported(clusterRef)) {
         if (ArrayUtils.isEmpty(vmCgInfo.getArchive())) {
            logger.info("No archival protection info for vm: {}", vmRef);
            return null;
         } else {
            ArchivalProtectionInfo archiveProtection = vmCgInfo.getArchive()[0];
            TreeSet<DataProtectionInstance> archivePits = new TreeSet(new DataProtectionSyncPointsService.DataProtectionInstanceComparator());
            SeriesEntry[] archivalSeries = this.cgProvider.getArchivalSeries(clusterRef, vmCgInfo.getKey());
            if (archivalSeries == null) {
               logger.debug("No archival series were found when querying archival pits for VM {}", vmRef);
               return archivePits;
            } else {
               SeriesEntry[] var10 = archivalSeries;
               int var9 = archivalSeries.length;

               for(int var8 = 0; var8 < var9; ++var8) {
                  SeriesEntry series = var10[var8];
                  GroupInstanceData[] pitsData = series.getInstance();
                  if (pitsData != null) {
                     GroupInstanceData[] var15 = pitsData;
                     int var14 = pitsData.length;

                     for(int var13 = 0; var13 < var14; ++var13) {
                        GroupInstanceData instance = var15[var13];
                        archivePits.add(DataProtectionInstance.createInstance(archiveProtection.series.key, ProtectionType.ARCHIVE, instance, vmRef, vmCgInfo.key));
                     }
                  }
               }

               if (CollectionUtils.isNotEmpty(archivePits)) {
                  logger.debug("Successfully retrieved archive PITs for VM {}. Total count of PITs found: {}", vmRef, archivePits.size());
               }

               return archivePits;
            }
         }
      } else {
         return null;
      }
   }

   public TreeSet<DataProtectionInstance> getRemotePits(ManagedObjectReference vmRef, CgInfo vmCgInfo) {
      ManagedObjectReference clusterRef = this.inventoryHelper.getVmCluster(vmRef);
      if (clusterRef != null && VsanCapabilityUtils.isRemoteDataProtectionSupported(clusterRef)) {
         if (ArrayUtils.isEmpty(vmCgInfo.getRemote())) {
            logger.info("No remote protection info for vm: {}", vmRef);
            return null;
         } else {
            RemoteProtectionInfo remoteProtection = vmCgInfo.getRemote()[0];
            TreeSet<DataProtectionInstance> remotePits = new TreeSet(new DataProtectionSyncPointsService.DataProtectionInstanceComparator());
            SeriesEntry[] remoteSeries = this.cgProvider.getRemoteSeries(clusterRef, vmCgInfo.getKey());
            if (remoteSeries == null) {
               logger.debug("No remote series were found for VM {}", vmRef);
               return remotePits;
            } else {
               SeriesEntry[] var10 = remoteSeries;
               int var9 = remoteSeries.length;

               for(int var8 = 0; var8 < var9; ++var8) {
                  SeriesEntry series = var10[var8];
                  GroupInstanceData[] pitsData = series.getInstance();
                  if (pitsData != null) {
                     GroupInstanceData[] var15 = pitsData;
                     int var14 = pitsData.length;

                     for(int var13 = 0; var13 < var14; ++var13) {
                        GroupInstanceData instance = var15[var13];
                        remotePits.add(DataProtectionInstance.createInstance(remoteProtection.series.key, ProtectionType.REMOTE, instance, vmRef, vmCgInfo.key));
                     }
                  }
               }

               if (CollectionUtils.isNotEmpty(remotePits)) {
                  logger.debug("Successfully retrieved remote PITs for VM {}. Total count of PITs found: {}", vmRef, remotePits.size());
               }

               return remotePits;
            }
         }
      } else {
         return null;
      }
   }

   public TreeSet<DataProtectionInstance> getTargetPits(CgInfo vmCgInfo) {
      if (vmCgInfo.getTarget() == null) {
         logger.info("No target replication info for cg key: {}", vmCgInfo.key);
         return null;
      } else {
         TargetProtectionInfo targetProtection = vmCgInfo.getTarget();
         TreeSet<DataProtectionInstance> targetPits = new TreeSet(new DataProtectionSyncPointsService.DataProtectionInstanceComparator());
         if (ArrayUtils.isEmpty(targetProtection.getInstance())) {
            logger.debug("No local protection PITs for cg key " + vmCgInfo.key);
            return targetPits;
         } else {
            TargetGroupInstanceData[] var7;
            int var6 = (var7 = targetProtection.getInstance()).length;

            for(int var5 = 0; var5 < var6; ++var5) {
               GroupInstanceData instance = var7[var5];
               targetPits.add(DataProtectionInstance.createInstance(targetProtection.series.key, ProtectionType.TARGET, instance, (ManagedObjectReference)null, vmCgInfo.key));
            }

            return targetPits;
         }
      }
   }
}
