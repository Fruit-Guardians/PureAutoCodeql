package com.vmware.vsan.client.services.fileservice.model;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

@data
public class VsanFileServiceCommonConfig {
   private static final Log logger = LogFactory.getLog(VsanFileServiceCommonConfig.class);
   public VsanFileServiceDomain domainConfig;
   public ManagedObjectReference network;
}
