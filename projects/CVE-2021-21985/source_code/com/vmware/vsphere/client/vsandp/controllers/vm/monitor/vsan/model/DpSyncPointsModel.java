package com.vmware.vsphere.client.vsandp.controllers.vm.monitor.vsan.model;

import com.vmware.vise.core.model.data;
import com.vmware.vsphere.client.vsandp.controllers.vm.monitor.vsan.model.filter.DataProtectionInstanceFilter;
import com.vmware.vsphere.client.vsandp.data.DataProtectionInstance;
import com.vmware.vsphere.client.vsandp.data.ProtectionType;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@data
public class DpSyncPointsModel {
   public Date lowestDate;
   public Date highestDate;
   public List<ProtectionType> protectionTypes = new ArrayList();
   public List<Date> headerInstances = new ArrayList();
   public Map<ProtectionType, TreeSet<DataProtectionInstance>> instances = new HashMap();
   public long instancesCount;
   public long totalLocalSnapshotsSize;
   public long totalArchiveSnapshotsSize;
   public long totalRemoteSnapshotsSize;
   public boolean hasRestorePermission;
   public boolean isTestAvailable;
   public boolean isCleanupAvailable;
   public DataProtectionInstanceFilter filterModel;
}
