package com.vmware.vsan.client.services.resyncing.data;

import com.vmware.vise.core.model.data;

@data
public class VsanSyncingObjectsQuerySpec {
   public int start = 0;
   public int limit = Integer.MAX_VALUE;
   public boolean includeSummary = true;
   public String[] resyncTypes;
   public String status;
}
