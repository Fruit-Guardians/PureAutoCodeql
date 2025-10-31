package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfEntityMetricCSV;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfMetricSeriesCSV;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.ArrayUtils;

public class PerfMetricsInfo {
   public Map<String, Integer> entityRefIdToIntervalMap;
   public Map<String, List<String>> entityRefIdToMetricIdMap;

   public static PerfMetricsInfo extractMetricsInfo(VsanPerfEntityMetricCSV[] metrics) {
      if (ArrayUtils.isEmpty(metrics)) {
         return new PerfMetricsInfo();
      } else {
         Map<String, Integer> intervals = new HashMap();
         Map<String, List<String>> metricIds = new HashMap();

         for(int metricIndex = 0; metricIndex < metrics.length; ++metricIndex) {
            String entityRefId = metrics[metricIndex].entityRefId;
            if (metricIds.get(entityRefId) == null && ArrayUtils.isNotEmpty(metrics[metricIndex].value)) {
               List<String> ids = new ArrayList();
               VsanPerfMetricSeriesCSV[] var9;
               int var8 = (var9 = metrics[metricIndex].value).length;

               for(int var7 = 0; var7 < var8; ++var7) {
                  VsanPerfMetricSeriesCSV value = var9[var7];
                  ids.add(value.metricId.label);
               }

               metricIds.put(entityRefId, ids);
            }

            if (intervals.get(entityRefId) == null) {
               int interval = PerfEntityStateData.getMetricsCollectInterval(metrics[metricIndex]);
               if (interval != 0) {
                  intervals.put(entityRefId, interval);
               }
            }
         }

         PerfMetricsInfo metricsInfo = new PerfMetricsInfo();
         metricsInfo.entityRefIdToIntervalMap = intervals;
         metricsInfo.entityRefIdToMetricIdMap = metricIds;
         return metricsInfo;
      }
   }
}
