package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfQuerySpec;
import com.vmware.vise.core.model.data;
import com.vmware.vsphere.client.vsan.base.util.BaseUtils;
import java.util.Calendar;

@data
public class PerfQuerySpec extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public String entityType;
   public String entityUuid;
   public String group;
   public Long startTime;
   public Long endTime;
   public Integer interval;
   public String[] labels;

   public static VsanPerfQuerySpec toVmodl(PerfQuerySpec spec) {
      VsanPerfQuerySpec querySpec = new VsanPerfQuerySpec();
      querySpec.endTime = getCalendarFromLong(spec.endTime);
      querySpec.startTime = getCalendarFromLong(spec.startTime);
      querySpec.group = spec.group;
      querySpec.interval = spec.interval;
      querySpec.labels = spec.labels;
      querySpec.entityRefId = spec.entityType + ":" + spec.entityUuid;
      return querySpec;
   }

   private static Calendar getCalendarFromLong(Long time) {
      Calendar calendar = Calendar.getInstance();
      calendar.setTimeInMillis(time);
      BaseUtils.setUTCTimeZone(calendar);
      return calendar;
   }
}
