package com.vmware.vsphere.client.vsan.perf;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.fault.NotFound;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vmomi.core.Future;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfDiagnoseFeedbackSpec;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfDiagnoseQuerySpec;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfDiagnosticException;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfDiagnosticResult;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfEntityInfo;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfEntityMetricCSV;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfEntityType;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerformanceManager;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.base.util.BaseUtils;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.perf.model.DiagnosticException;
import com.vmware.vsphere.client.vsan.perf.model.EntityRefData;
import com.vmware.vsphere.client.vsan.perf.model.PerfDiagnosticQuerySpec;
import com.vmware.vsphere.client.vsan.perf.model.PerformanceDiagnosticData;
import com.vmware.vsphere.client.vsan.perf.model.PerformanceDiagnosticException;
import com.vmware.vsphere.client.vsan.perf.model.PerformanceEntitiesData;
import com.vmware.vsphere.client.vsan.perf.model.PerformanceExceptionsData;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class PerformanceDiagnosticsPropertyProvider {
   private static final Log _logger = LogFactory.getLog(PerformanceDiagnosticsPropertyProvider.class);
   private static final VsanProfiler _profiler = new VsanProfiler(PerformanceDiagnosticsPropertyProvider.class);

   @TsService
   public boolean getPerfAnalysisSupported(ManagedObjectReference clusterRef) throws Exception {
      return VsanCapabilityUtils.isPerfAnalysisSupportedOnVc(clusterRef);
   }

   @TsService
   public PerformanceExceptionsData getPerformanceExceptionsData(ManagedObjectReference clusterRef) throws Exception {
      VsanPerformanceManager perfMgr = VsanProviderUtils.getVsanPerformanceManager(clusterRef);
      VsanPerfDiagnosticException[] exceptions = this.getExceptions(perfMgr);
      Map<String, PerformanceDiagnosticException> idToExceptionMap = new HashMap();
      if (exceptions != null) {
         VsanPerfDiagnosticException[] var8 = exceptions;
         int var7 = exceptions.length;

         for(int var6 = 0; var6 < var7; ++var6) {
            VsanPerfDiagnosticException ex = var8[var6];
            idToExceptionMap.put(ex.exceptionId, new PerformanceDiagnosticException(ex.exceptionMessage, ex.exceptionDetails, ex.exceptionUrl));
         }
      }

      PerformanceExceptionsData exceptionsData = new PerformanceExceptionsData();
      exceptionsData.performanceExceptionIdToException = idToExceptionMap;
      PerformanceDiagnosticsPropertyProvider.EntityTypes types = this.getVsanPerfEntityTypes(perfMgr);
      exceptionsData.performanceEntityTypes = types.simpleTypes;
      exceptionsData.performanceAggregatedEntityTypes = types.aggregatedTypes;
      return exceptionsData;
   }

   private VsanPerfDiagnosticException[] getExceptions(VsanPerformanceManager perfMgr) {
      VsanPerfDiagnosticException[] exceptions = null;
      Throwable var3 = null;
      Object var4 = null;

      try {
         VsanProfiler.Point p = _profiler.point("perfMgr.getSupportedDiagnosticExceptions");

         try {
            exceptions = perfMgr.getSupportedDiagnosticExceptions();
         } finally {
            if (p != null) {
               p.close();
            }

         }

         return exceptions;
      } catch (Throwable var11) {
         if (var3 == null) {
            var3 = var11;
         } else if (var3 != var11) {
            var3.addSuppressed(var11);
         }

         throw var3;
      }
   }

   @TsService
   public PerformanceDiagnosticData getPerformanceDiagnosticData(ManagedObjectReference clusterRef, ManagedObjectReference taskRef, PerfDiagnosticQuerySpec spec) {
      Validate.notNull(taskRef);
      VsanPerformanceManager perfMgr = VsanProviderUtils.getVsanPerformanceManager(clusterRef);
      VsanPerfDiagnosticResult[] perfDiagnosticResults = this.getDiagnosticResults(perfMgr, taskRef, clusterRef);
      int totalPerfDiagIssues = perfDiagnosticResults == null ? 0 : perfDiagnosticResults.length;
      _logger.info("Total received number of performance issues is " + totalPerfDiagIssues);
      if (perfDiagnosticResults != null && perfDiagnosticResults.length != 0) {
         List<DiagnosticException> issues = this.getIssues(perfDiagnosticResults, spec.startTime.getTimeInMillis(), spec.endTime.getTimeInMillis());
         PerformanceDiagnosticData result = new PerformanceDiagnosticData(issues, this.getAllEntityRefIds(perfDiagnosticResults));
         return result;
      } else {
         DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
         _logger.info(String.format("No performance issues were detected for the period between %s and %s for perspective: %s", dateFormat.format(spec.startTime.getTime()), dateFormat.format(spec.endTime.getTime()), spec.queryType.toString()));
         return new PerformanceDiagnosticData();
      }
   }

   @TsService
   public ManagedObjectReference getPerformanceDiagnosticTask(ManagedObjectReference clusterRef, PerfDiagnosticQuerySpec spec) {
      VsanPerformanceManager perfMgr = VsanProviderUtils.getVsanPerformanceManager(clusterRef);
      VsanPerfDiagnoseQuerySpec querySpec = new VsanPerfDiagnoseQuerySpec();
      querySpec.queryType = spec.queryType.toString();
      querySpec.startTime = spec.startTime;
      BaseUtils.setUTCTimeZone(querySpec.startTime);
      querySpec.endTime = spec.endTime;
      BaseUtils.setUTCTimeZone(querySpec.endTime);
      ManagedObjectReference perfDiagnosticTaskRef = null;

      try {
         Throwable var6 = null;
         Object var7 = null;

         try {
            VsanProfiler.Point p = _profiler.point("perfMgr.vsanPerfDiagnoseTask");

            try {
               perfDiagnosticTaskRef = perfMgr.vsanPerfDiagnoseTask(querySpec, clusterRef);
               VmodlHelper.assignServerGuid(perfDiagnosticTaskRef, clusterRef.getServerGuid());
            } finally {
               if (p != null) {
                  p.close();
               }

            }

            return perfDiagnosticTaskRef;
         } catch (Throwable var16) {
            if (var6 == null) {
               var6 = var16;
            } else if (var6 != var16) {
               var6.addSuppressed(var16);
            }

            throw var6;
         }
      } catch (Exception var17) {
         _logger.error("Cannot trigger performance diagnose task", var17);
         throw new VsanUiLocalizableException("vsan.perf.query.task.error");
      }
   }

   @TsService
   public PerformanceEntitiesData getPerfEntitiesInfo(ManagedObjectReference clusterRef, List<String> entityRefIds) throws Exception {
      VsanPerformanceManager perfMgr = VsanProviderUtils.getVsanPerformanceManager(clusterRef);
      Map<String, EntityRefData> entityRefIdToEntityRefDataMap = this.getEntityRefIdToEntityRefDataMap((String[])entityRefIds.toArray(new String[entityRefIds.size()]), perfMgr, clusterRef);
      return new PerformanceEntitiesData(entityRefIdToEntityRefDataMap);
   }

   @TsService
   public void submitFeedbackForDiagnosisResult(ManagedObjectReference clusterRef, VsanPerfDiagnoseFeedbackSpec feedbackSpec, boolean feedbackValue) {
      VsanPerformanceManager perfMgr = VsanProviderUtils.getVsanPerformanceManager(clusterRef);
      if (!VsanCapabilityUtils.isPerfDiagnosticsFeedbackSupportedOnVc(clusterRef)) {
         throw new VsanUiLocalizableException("vsan.perf.feedback.submit.error");
      } else {
         boolean isFeedbackSubmitted = false;

         try {
            Throwable var6 = null;
            Object var7 = null;

            try {
               VsanProfiler.Point p = _profiler.point("perfMgr.submitFeedbackForDiagnosisResult");

               try {
                  isFeedbackSubmitted = perfMgr.submitFeedbackForDiagnosisResult(feedbackSpec, feedbackValue, (String)null, clusterRef);
                  if (!isFeedbackSubmitted) {
                     _logger.error("Could not submit feedback: return false");
                     throw new VsanUiLocalizableException("vsan.perf.feedback.submit.error");
                  }
               } finally {
                  if (p != null) {
                     p.close();
                  }

               }

            } catch (Throwable var16) {
               if (var6 == null) {
                  var6 = var16;
               } else if (var6 != var16) {
                  var6.addSuppressed(var16);
               }

               throw var6;
            }
         } catch (Exception var17) {
            _logger.error("Could not submit feedback", var17);
            throw new VsanUiLocalizableException("vsan.perf.feedback.submit.error");
         }
      }
   }

   private List<DiagnosticException> getIssues(VsanPerfDiagnosticResult[] perfDiagnosticResults, long rangeStart, long rangeEnd) {
      List<DiagnosticException> issues = new ArrayList();
      Map<String, DiagnosticException> idToExceptionMap = new HashMap();
      VsanPerfDiagnosticResult[] var11 = perfDiagnosticResults;
      int var10 = perfDiagnosticResults.length;

      for(int var9 = 0; var9 < var10; ++var9) {
         VsanPerfDiagnosticResult diagnosticResult = var11[var9];
         _logger.info(String.format("Preparing perf diag issue for exceptionId: %s, and recommendation: %s", diagnosticResult.exceptionId, diagnosticResult.recommendation));
         DiagnosticException diagEx = (DiagnosticException)idToExceptionMap.get(diagnosticResult.exceptionId);
         if (diagEx == null) {
            diagEx = new DiagnosticException(diagnosticResult.exceptionId);
            idToExceptionMap.put(diagEx.exceptionId, diagEx);
            issues.add(diagEx);
         }

         diagEx.addEntities(diagnosticResult, rangeStart, rangeEnd);
      }

      return issues;
   }

   private List<String> getAllEntityRefIds(VsanPerfDiagnosticResult[] perfDiagnosticResults) {
      List<String> entityRefIds = new ArrayList();
      VsanPerfDiagnosticResult[] var6 = perfDiagnosticResults;
      int var5 = perfDiagnosticResults.length;

      for(int var4 = 0; var4 < var5; ++var4) {
         VsanPerfDiagnosticResult diagResult = var6[var4];
         if (!ArrayUtils.isEmpty(diagResult.exceptionData)) {
            VsanPerfEntityMetricCSV[] var10;
            int var9 = (var10 = diagResult.exceptionData).length;

            for(int var8 = 0; var8 < var9; ++var8) {
               VsanPerfEntityMetricCSV metricCsv = var10[var8];
               if (!entityRefIds.contains(metricCsv.entityRefId)) {
                  entityRefIds.add(metricCsv.entityRefId);
               }
            }
         }
      }

      return entityRefIds;
   }

   protected VsanPerfDiagnosticResult[] getDiagnosticResults(VsanPerformanceManager perfMgr, ManagedObjectReference taskRef, ManagedObjectReference clusterRef) {
      VsanPerfDiagnosticResult[] perfDiagnosticResults = null;

      try {
         Throwable var5 = null;
         Object var6 = null;

         try {
            VsanProfiler.Point p = _profiler.point("perfMgr.getVsanPerfDiagnosisResult");

            try {
               if (taskRef != null) {
                  perfDiagnosticResults = perfMgr.getVsanPerfDiagnosisResult(taskRef, clusterRef);
               }
            } finally {
               if (p != null) {
                  p.close();
               }

            }
         } catch (Throwable var17) {
            if (var5 == null) {
               var5 = var17;
            } else if (var5 != var17) {
               var5.addSuppressed(var17);
            }

            throw var5;
         }
      } catch (NotFound var18) {
         _logger.info("There is no diagnostic data in the selected time period.", var18);
      } catch (Exception var19) {
         _logger.error("Could not retrieve performance diagnostic issues", var19);
         throw new VsanUiLocalizableException("vsan.perf.query.issues.error");
      }

      return perfDiagnosticResults;
   }

   private Map<String, EntityRefData> getEntityRefIdToEntityRefDataMap(String[] entityRefIds, VsanPerformanceManager perfMgr, ManagedObjectReference clusterRef) throws Exception {
      Map<String, EntityRefData> result = new HashMap();
      if (ArrayUtils.isEmpty(entityRefIds)) {
         return result;
      } else {
         VsanPerfEntityInfo[] entityInfos = perfMgr.getVcMoRefFromPerfEntityRefId(clusterRef, entityRefIds);
         if (entityInfos != null) {
            VsanPerfEntityInfo[] var9 = entityInfos;
            int var8 = entityInfos.length;

            for(int var7 = 0; var7 < var8; ++var7) {
               VsanPerfEntityInfo entityInfo = var9[var7];
               EntityRefData refData = new EntityRefData(entityInfo, clusterRef);
               if (refData.managedObjectRef == null) {
                  _logger.info(String.format("Skipping entity with entityRefId %s as missing", entityInfo.entityRefId));
               }

               result.put(entityInfo.entityRefId, refData);
            }
         }

         if (!result.isEmpty()) {
            Map<ManagedObjectReference, String> refToNameMap = this.getMoRefToNameMap(result.values());

            EntityRefData refData;
            for(Iterator var13 = result.values().iterator(); var13.hasNext(); refData.managedObjectName = (String)refToNameMap.get(refData.managedObjectRef)) {
               refData = (EntityRefData)var13.next();
            }
         }

         return result;
      }
   }

   private Map<ManagedObjectReference, String> getMoRefToNameMap(Collection<EntityRefData> refDatas) throws Exception {
      Set<ManagedObjectReference> mos = new HashSet();
      Iterator var4 = refDatas.iterator();

      while(var4.hasNext()) {
         EntityRefData refData = (EntityRefData)var4.next();
         if (!refData.isEntityMissing) {
            mos.add(refData.managedObjectRef);
         }
      }

      Map<ManagedObjectReference, String> refToNameMap = new HashMap();
      if (mos.size() > 0) {
         PropertyValue[] propValues = QueryUtil.getProperties((ManagedObjectReference[])mos.toArray(new ManagedObjectReference[0]), new String[]{"name"}).getPropertyValues();
         PropertyValue[] var8 = propValues;
         int var7 = propValues.length;

         for(int var6 = 0; var6 < var7; ++var6) {
            PropertyValue propValue = var8[var6];
            refToNameMap.put((ManagedObjectReference)propValue.resourceObject, (String)propValue.value);
         }
      }

      return refToNameMap;
   }

   private PerformanceDiagnosticsPropertyProvider.EntityTypes getVsanPerfEntityTypes(VsanPerformanceManager performanceManager) throws Exception {
      Throwable var2 = null;
      Object var3 = null;

      try {
         Measure measure = new Measure("Retrieving performance diagnostics entity types");

         Throwable var10000;
         label173: {
            boolean var10001;
            PerformanceDiagnosticsPropertyProvider.EntityTypes var21;
            try {
               Future<VsanPerfEntityType[]> entityTypesFuture = this.getEntityTypesFuture(performanceManager, measure);
               Future<VsanPerfEntityType[]> aggregatedEntityTypesFuture = this.getAggregatedEntityTypesFuture(performanceManager, measure);
               Map<String, VsanPerfEntityType> entityTypes = this.getEntityTypesFromFuture(entityTypesFuture);
               Map<String, VsanPerfEntityType> aggregatedEntityTypes = this.getEntityTypesFromFuture(aggregatedEntityTypesFuture);
               var21 = new PerformanceDiagnosticsPropertyProvider.EntityTypes(entityTypes, aggregatedEntityTypes);
            } catch (Throwable var19) {
               var10000 = var19;
               var10001 = false;
               break label173;
            }

            if (measure != null) {
               measure.close();
            }

            label162:
            try {
               return var21;
            } catch (Throwable var18) {
               var10000 = var18;
               var10001 = false;
               break label162;
            }
         }

         var2 = var10000;
         if (measure != null) {
            measure.close();
         }

         throw var2;
      } catch (Throwable var20) {
         if (var2 == null) {
            var2 = var20;
         } else if (var2 != var20) {
            var2.addSuppressed(var20);
         }

         throw var2;
      }
   }

   private Future<VsanPerfEntityType[]> getEntityTypesFuture(VsanPerformanceManager performanceManager, Measure measure) {
      Future<VsanPerfEntityType[]> future = measure.newFuture("VsanPerformanceManager.getSupportedEntityTypes");
      performanceManager.getSupportedEntityTypes(future);
      return future;
   }

   private Future<VsanPerfEntityType[]> getAggregatedEntityTypesFuture(VsanPerformanceManager perfMgr, Measure measure) {
      Future<VsanPerfEntityType[]> future = measure.newFuture("VsanPerformanceManager.getAggregatedEntityTypes");
      perfMgr.getAggregatedEntityTypes(future);
      return future;
   }

   private Map<String, VsanPerfEntityType> getEntityTypesFromFuture(Future<VsanPerfEntityType[]> future) {
      Object entityTypeMap = new HashMap();

      try {
         VsanPerfEntityType[] entityTypes = (VsanPerfEntityType[])future.get();
         entityTypeMap = createNameToTypeMap(entityTypes);
      } catch (Exception var4) {
         _logger.error("Cannot load supported entity types: ", var4);
      }

      return (Map)entityTypeMap;
   }

   private static Map<String, VsanPerfEntityType> createNameToTypeMap(VsanPerfEntityType[] types) {
      Map<String, VsanPerfEntityType> map = new HashMap();
      if (types != null) {
         VsanPerfEntityType[] var5 = types;
         int var4 = types.length;

         for(int var3 = 0; var3 < var4; ++var3) {
            VsanPerfEntityType perfEntityType = var5[var3];
            map.put(perfEntityType.name, perfEntityType);
         }
      }

      return map;
   }

   private static class EntityTypes {
      Map<String, VsanPerfEntityType> simpleTypes;
      Map<String, VsanPerfEntityType> aggregatedTypes;

      public EntityTypes(Map<String, VsanPerfEntityType> simpleTypes, Map<String, VsanPerfEntityType> aggregatedTyped) {
         this.simpleTypes = simpleTypes;
         this.aggregatedTypes = aggregatedTyped;
      }
   }
}
