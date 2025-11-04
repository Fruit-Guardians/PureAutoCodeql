package com.vmware.vsan.client.services.dataprotection.model;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;
import java.util.ArrayList;
import java.util.List;

@data
public class ClusterDpConfigData {
   public Integer consumptionLimit;
   public Integer remoteReplicationPort;
   public boolean isRemoteDpSupported;
   public ManagedObjectReference remoteVcRef;
   public String remoteVcName;
   public ManagedObjectReference remoteClusterRef;
   public String remoteClusterName;
   public ManagedObjectReference sourceDatastoreRef;
   public String sourceDatastoreUrl;
   public String lsHost;
   public Integer lsPort;
   public String lsThumbprint;
   public boolean isArchiveDpSupported;
   public String archivalDpDatastoreName;
   public String archivalDpDatastoreUrl;
   public ManagedObjectReference archivalDpDatastoreRef;
   public List<String> errorMessages = new ArrayList();
}
