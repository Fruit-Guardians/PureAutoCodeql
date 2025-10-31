package com.vmware.vsphere.client.vsan.iscsi.models.config;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTargetServiceConfig;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectInformation;
import com.vmware.vise.core.model.data;

@data
public class VsanIscsiConfig extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public VsanIscsiTargetServiceConfig vsanIscsiTargetServiceConfig;
   public VsanObjectInformation vsanObjectInformation;
}
