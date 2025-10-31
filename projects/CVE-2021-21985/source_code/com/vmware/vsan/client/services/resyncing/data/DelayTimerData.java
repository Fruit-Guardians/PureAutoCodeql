package com.vmware.vsan.client.services.resyncing.data;

import com.vmware.vise.core.model.data;

@data
public class DelayTimerData {
   public long delayTimer;
   public boolean isSupported;
   public String errorMessage;
}
