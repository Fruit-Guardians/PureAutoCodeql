package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfDiagnosticResult;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfEntityMetricCSV;
import com.vmware.vise.core.model.data;
import java.util.ArrayList;
import java.util.List;

@data
public class DiagnosticException extends DataObjectImpl {
   private static final String AGGREGATED_REF_ID_SEPARATOR = "/";
   private static final long serialVersionUID = 1L;
   public String exceptionId;
   public List<DiagnosticIssueEntity> exceptionEntities;

   public DiagnosticException() {
      this.exceptionEntities = new ArrayList();
   }

   public DiagnosticException(String exceptionId) {
      this();
      this.exceptionId = exceptionId;
   }

   public void addEntities(VsanPerfDiagnosticResult diagnosticResult, long rangeStart, long rangeEnd) {
      if (diagnosticResult.aggregationData != null) {
         AggregatedDiagnosticIssueEntity aggregatedEntity = this.createAggregatedEntity(diagnosticResult, rangeStart, rangeEnd);
         if (!aggregatedEntity.entities.isEmpty()) {
            this.exceptionEntities.add(aggregatedEntity);
         }
      } else {
         VsanPerfEntityMetricCSV[] var9;
         int var8 = (var9 = diagnosticResult.exceptionData).length;

         for(int var7 = 0; var7 < var8; ++var7) {
            VsanPerfEntityMetricCSV entityMetric = var9[var7];
            SingleDiagnosticIssueEntity issueEntity = new SingleDiagnosticIssueEntity(diagnosticResult.recommendation, PerfEntityStateData.parsePerfEntityMetricCSV(entityMetric, rangeStart, rangeEnd));
            this.exceptionEntities.add(issueEntity);
         }
      }

   }

   private AggregatedDiagnosticIssueEntity createAggregatedEntity(VsanPerfDiagnosticResult diagnosticResult, long rangeStart, long rangeEnd) {
      String[] aggregatedEntityRefIds = diagnosticResult.aggregationData.entityRefId.split("/");

      for(int i = 0; i < aggregatedEntityRefIds.length; ++i) {
         String[] parts = aggregatedEntityRefIds[i].split(":");
         aggregatedEntityRefIds[i] = parts[0];
      }

      AggregatedDiagnosticIssueEntity issue = new AggregatedDiagnosticIssueEntity(diagnosticResult.recommendation, diagnosticResult.aggregationData, diagnosticResult.exceptionData, rangeStart, rangeEnd);
      return issue;
   }
}
