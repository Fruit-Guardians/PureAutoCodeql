package com.vmware.vsan.client.services.cns.model;

import com.vmware.vim.vsan.binding.vim.cns.KubernetesEntityMetadata;
import com.vmware.vise.core.model.data;
import com.vmware.vsan.client.services.common.data.BasicVmData;
import com.vmware.vsan.client.services.common.data.StorageCompliance;
import java.util.ArrayList;
import java.util.List;

@data
public class Volume {
   public String id;
   public String name;
   public String type;
   public String storagePolicyId;
   public String containerCluster;
   public List<BasicVmData> vmData;
   public List<VolumeDatastoreData> datastoreData;
   public List<KubernetesEntityMetadata> persistentVolumeMetadata = new ArrayList();
   public List<KubernetesEntityMetadata> persistentVolumeClaimMetadata = new ArrayList();
   public List<String> podNames = new ArrayList();
   public StorageCompliance compliance;
   public List<CnsLabel> labels;
   public CnsDatastoreAccessibilityStatus accessibility;
   public boolean isVsanDatastore;
   public long capacity;
}
