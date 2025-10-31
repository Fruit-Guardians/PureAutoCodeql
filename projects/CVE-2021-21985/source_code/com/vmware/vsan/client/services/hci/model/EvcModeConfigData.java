package com.vmware.vsan.client.services.hci.model;

import com.vmware.vise.core.model.data;
import java.util.List;

@data
public class EvcModeConfigData {
   public boolean enabled;
   public boolean unsupportedEvcStatus;
   public EvcModeData selectedEvcMode;
   public List<EvcModeData> supportedIntelEvcMode;
   public List<EvcModeData> supportedAmdEvcMode;
}
