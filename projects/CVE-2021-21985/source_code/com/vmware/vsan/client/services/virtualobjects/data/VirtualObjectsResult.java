package com.vmware.vsan.client.services.virtualobjects.data;

import com.vmware.vise.core.model.data;
import com.vmware.vsphere.client.vsan.base.data.VsanObjectDataProtectionHealthState;
import com.vmware.vsphere.client.vsan.base.data.VsanObjectHealthState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@data
public class VirtualObjectsResult {
   public Map<VsanObjectHealthState, Integer> countByObjectHealth = new HashMap();
   public Map<VsanObjectDataProtectionHealthState, Integer> countByDataProtectionHealth = new HashMap();
   public List<VirtualObjectModel> items = new ArrayList();
}
