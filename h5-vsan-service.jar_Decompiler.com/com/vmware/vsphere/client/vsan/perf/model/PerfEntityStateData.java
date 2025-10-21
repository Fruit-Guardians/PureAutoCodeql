package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfEntityMetricCSV;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfMetricSeriesCSV;
import com.vmware.vise.core.model.data;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

@data
public class PerfEntityStateData {
   private static final String NONE = "None";
   public List<String> timeStamps;
   public List<PerfGraphMetricsData> metricsSeries;
   public int metricsCollectInterval;
   public String entityRefId;

   public static PerfEntityStateData parsePerfEntityMetricCSV(VsanPerfEntityMetricCSV metric, long rangeStart, long rangeEnd) {
      PerfEntityStateData stateData = new PerfEntityStateData();
      stateData.entityRefId = metric.entityRefId;
      stateData.timeStamps = new ArrayList();
      stateData.metricsSeries = new ArrayList();
      if (!ArrayUtils.isEmpty(metric.value) && !StringUtils.isEmpty(metric.sampleInfo)) {
         List<String> rawTimestamps = Arrays.asList(StringUtils.split(metric.sampleInfo, ","));
         int metricsCollectInterval = getMetricsCollectInterval(metric);
         stateData.metricsCollectInterval = metricsCollectInterval;
         boolean canFormatData = metricsCollectInterval > 0;
         if (canFormatData) {
            stateData.timeStamps = generateTimestamps(rangeStart, rangeEnd, metricsCollectInterval);
         } else {
            stateData.timeStamps = rawTimestamps;
         }

         VsanPerfMetricSeriesCSV[] var12;
         int var11 = (var12 = metric.value).length;

         for(int var10 = 0; var10 < var11; ++var10) {
            VsanPerfMetricSeriesCSV metricSeries = var12[var10];
            PerfGraphMetricsData metricsData = new PerfGraphMetricsData();
            metricsData.key = metricSeries.metricId.label;
            if (metricSeries.threshold != null) {
               metricsData.threshold = new PerfGraphThreshold();
               metricsData.threshold.direction = PerfGraphThresholdDirection.fromVmodl(metricSeries.threshold.direction);
               metricsData.threshold.yellow = StringUtils.isBlank(metricSeries.threshold.yellow) ? null : Long.parseLong(metricSeries.threshold.yellow);
               metricsData.threshold.red = StringUtils.isBlank(metricSeries.threshold.red) ? null : Long.parseLong(metricSeries.threshold.red);
            }

            String[] rawValues = metricSeries.values.split(",");
            List<Double> newValues = new ArrayList();
            int index;
            if (!canFormatData) {
               String[] var23 = rawValues;
               int var22 = rawValues.length;

               for(index = 0; index < var22; ++index) {
                  String value = var23[index];
                  newValues.add(formatValue(value));
               }
            } else {
               Map<String, String> timestampToValueMapOfRawData = new HashMap();

               for(index = 0; index < rawValues.length; ++index) {
                  timestampToValueMapOfRawData.put((String)rawTimestamps.get(index), rawValues[index]);
               }

               Iterator var18 = stateData.timeStamps.iterator();

               while(var18.hasNext()) {
                  String timestamp = (String)var18.next();
                  String value = (String)timestampToValueMapOfRawData.get(timestamp);
                  newValues.add(formatValue(value));
               }
            }

            metricsData.values = newValues;
            stateData.metricsSeries.add(metricsData);
         }

         return stateData;
      } else {
         return stateData;
      }
   }

   protected static int getMetricsCollectInterval(VsanPerfEntityMetricCSV metric) {
      int metricsCollectInterval = 0;
      if (ArrayUtils.isEmpty(metric.value)) {
         return metricsCollectInterval;
      } else {
         VsanPerfMetricSeriesCSV[] var5;
         int var4 = (var5 = metric.value).length;

         for(int var3 = 0; var3 < var4; ++var3) {
            VsanPerfMetricSeriesCSV value = var5[var3];
            if (value.metricId != null && value.metricId.metricsCollectInterval != null && value.metricId.metricsCollectInterval > 0) {
               return value.metricId.metricsCollectInterval;
            }
         }

         return metricsCollectInterval;
      }
   }

   private static Double formatValue(String valueStr) {
      return !"None".equalsIgnoreCase(valueStr) && !StringUtils.isBlank(valueStr) ? Double.parseDouble(valueStr) : null;
   }

   public static PerfEntityStateData parsePerfEntityMetricCSV(VsanPerfEntityMetricCSV metric, long rangeStart, long rangeEnd, int interval, List<String> metricIds) {
      PerfEntityStateData stateData = new PerfEntityStateData();
      stateData.metricsCollectInterval = interval;
      stateData.entityRefId = metric.entityRefId;
      stateData.timeStamps = generateTimestamps(rangeStart, rangeEnd, interval);
      stateData.metricsSeries = new ArrayList();
      Iterator var9 = metricIds.iterator();

      while(var9.hasNext()) {
         String id = (String)var9.next();
         PerfGraphMetricsData metricsData = new PerfGraphMetricsData();
         metricsData.key = id;

         for(int index = 0; index < stateData.timeStamps.size(); ++index) {
            if (metricsData.values == null) {
               metricsData.values = new ArrayList();
            }

            metricsData.values.add((Object)null);
         }

         stateData.metricsSeries.add(metricsData);
      }

      return stateData;
   }

   private static List<String> generateTimestamps(long rangeStart, long rangeEnd, int interval) {
      DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      List<String> timestamps = new ArrayList();

      for(rangeStart %= (long)(interval * 1000); rangeStart <= rangeEnd; rangeStart += (long)(interval * 1000)) {
         timestamps.add(dateFormat.format(new Date(rangeStart)));
      }

      return timestamps;
   }
}
