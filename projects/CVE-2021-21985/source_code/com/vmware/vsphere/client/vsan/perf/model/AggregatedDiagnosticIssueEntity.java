package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfEntityMetricCSV;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfMetricSeriesCSV;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfThreshold;
import com.vmware.vise.core.model.data;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.StringUtils;

@data
public class AggregatedDiagnosticIssueEntity extends DiagnosticIssueEntity {
   private static final String AGGREGATED_REF_ID_SEPARATOR = "/";
   private static final long serialVersionUID = 1L;
   public String[] aggregatedRefIds;
   public List<SingleDiagnosticIssueEntity> entities;
   public SingleDiagnosticIssueEntity aggregatedEntity;
   public boolean hasSingleEntityInside;
   public boolean usingSingleMetricForAllEntities = false;
   public String metricIdLabel;
   public VsanPerfThreshold aggregationThreshold;

   public AggregatedDiagnosticIssueEntity() {
   }

   public AggregatedDiagnosticIssueEntity(String recommendation, VsanPerfEntityMetricCSV aggregationData, VsanPerfEntityMetricCSV[] metrics, long rangeStart, long rangeEnd) {
      super(recommendation);
      this.hasSingleEntityInside = this.isAggregatingMetricsOnSingleEntity(metrics);
      if (this.hasSingleEntityInside) {
         this.aggregatedRefIds = new String[]{aggregationData.entityRefId};
      } else {
         this.aggregatedRefIds = aggregationData.entityRefId.split("/");

         for(int i = 0; i < this.aggregatedRefIds.length; ++i) {
            String[] parts = this.aggregatedRefIds[i].split(":");
            this.aggregatedRefIds[i] = parts[0];
         }

         this.metricIdLabel = this.getCommonMetricLabelIfUsingCommonMetric(metrics);
         this.usingSingleMetricForAllEntities = StringUtils.isNotEmpty(this.metricIdLabel);
      }

      this.aggregatedEntity = new SingleDiagnosticIssueEntity(PerfEntityStateData.parsePerfEntityMetricCSV(aggregationData, rangeStart, rangeEnd));
      this.entities = this.createChildEntities(metrics, rangeStart, rangeEnd);
      this.aggregationThreshold = aggregationData.value[0].threshold;
   }

   private boolean isAggregatingMetricsOnSingleEntity(VsanPerfEntityMetricCSV[] metrics) {
      boolean isSingleEntity = true;
      String entityRefId = "";
      VsanPerfEntityMetricCSV[] var7 = metrics;
      int var6 = metrics.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         VsanPerfEntityMetricCSV metric = var7[var5];
         if (StringUtils.isNotEmpty(entityRefId) && !entityRefId.equals(metric.entityRefId)) {
            isSingleEntity = false;
            break;
         }

         entityRefId = metric.entityRefId;
      }

      return isSingleEntity;
   }

   private List<SingleDiagnosticIssueEntity> createChildEntities(VsanPerfEntityMetricCSV[] metrics, long rangeStart, long rangeEnd) {
      List<SingleDiagnosticIssueEntity> _entities = new ArrayList();
      VsanPerfEntityMetricCSV[] var10 = metrics;
      int var9 = metrics.length;

      for(int var8 = 0; var8 < var9; ++var8) {
         VsanPerfEntityMetricCSV entityMetric = var10[var8];
         SingleDiagnosticIssueEntity entity = new SingleDiagnosticIssueEntity("", PerfEntityStateData.parsePerfEntityMetricCSV(entityMetric, rangeStart, rangeEnd));
         _entities.add(entity);
      }

      return _entities;
   }

   private String getCommonMetricLabelIfUsingCommonMetric(VsanPerfEntityMetricCSV[] metrics) {
      String metricIdLabel = null;
      boolean usingSingleMetricForAllEntities = true;
      VsanPerfEntityMetricCSV[] var7 = metrics;
      int var6 = metrics.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         VsanPerfEntityMetricCSV entityMetric = var7[var5];
         VsanPerfMetricSeriesCSV[] var11;
         int var10 = (var11 = entityMetric.value).length;

         for(int var9 = 0; var9 < var10; ++var9) {
            VsanPerfMetricSeriesCSV metricSeries = var11[var9];
            if (!StringUtils.isEmpty(metricIdLabel) && !metricIdLabel.equals(metricSeries.metricId.label)) {
               usingSingleMetricForAllEntities = false;
               break;
            }

            metricIdLabel = metricSeries.metricId.label;
         }

         if (!usingSingleMetricForAllEntities) {
            metricIdLabel = null;
            break;
         }
      }

      return metricIdLabel;
   }
}
