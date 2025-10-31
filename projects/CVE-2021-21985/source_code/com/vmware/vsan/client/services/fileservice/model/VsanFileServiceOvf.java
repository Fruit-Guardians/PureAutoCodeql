package com.vmware.vsan.client.services.fileservice.model;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.vsan.VsanFileServiceOvfSpec;
import com.vmware.vise.core.model.data;
import com.vmware.vsan.client.util.VmodlHelper;
import java.util.Calendar;
import java.util.Date;

@data
public class VsanFileServiceOvf {
   public Date updateTime;
   public String version;
   public ManagedObjectReference downloadTask;
   public boolean isCompatible;

   public static VsanFileServiceOvf fromVmodl(VsanFileServiceOvfSpec spec) {
      VsanFileServiceOvf ovf = new VsanFileServiceOvf();
      if (spec.updateTime != null) {
         ovf.updateTime = spec.updateTime.getTime();
      }

      ovf.version = spec.version;
      ovf.downloadTask = spec.task;
      return ovf;
   }

   public static VsanFileServiceOvf fromVmodl(VsanFileServiceOvfSpec spec, ManagedObjectReference originalMoRef) {
      VsanFileServiceOvf ovf = fromVmodl(spec);
      if (ovf.downloadTask != null) {
         VmodlHelper.assignServerGuid(ovf.downloadTask, originalMoRef.getServerGuid());
      }

      return ovf;
   }

   public VsanFileServiceOvfSpec toVmodl() {
      VsanFileServiceOvfSpec spec = new VsanFileServiceOvfSpec();
      spec.updateTime = Calendar.getInstance();
      spec.updateTime.setTime(this.updateTime);
      spec.version = this.version;
      return spec;
   }
}
