package com.vmware.vsan.client.services.hci.model;

import com.vmware.vise.core.model.data;
import java.util.Map;

@data
public class BasicClusterConfigData {
   public int hosts;
   public int notConfiguredHosts;
   public HciWorkflowState hciWorkflowState;
   public Map<Service, DvsData> dvsDataByService;
   public boolean haEnabled;
   public boolean drsEnabled;
   public boolean vsanEnabled;
}
