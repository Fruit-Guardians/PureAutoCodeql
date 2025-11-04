package com.vmware.vsphere.client.vsandp.controllers.vm.monitor.vsan.model.filter;

import com.vmware.vise.core.model.data;

@data
public enum DataProtectionInstanceFilterEnum {
   NEWER_THAN_THREE_DAYS,
   THREE_SEVEN_DAYS,
   ONE_TWO_WEEKS,
   TWO_FOUR_WEEKS,
   OLDER_THAN_FOUR_WEEKS;
}
