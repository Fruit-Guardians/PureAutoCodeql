package com.vmware.vsphere.client.vsan.stretched;

import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.data.Constraint;
import com.vmware.vise.data.ResourceSpec;
import com.vmware.vise.data.query.DataException;
import com.vmware.vise.data.query.DataProviderAdapter;
import com.vmware.vise.data.query.DataServiceExtensionRegistry;
import com.vmware.vise.data.query.ObjectIdentityConstraint;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.QuerySpec;
import com.vmware.vise.data.query.RelationalConstraint;
import com.vmware.vise.data.query.RequestSpec;
import com.vmware.vise.data.query.Response;
import com.vmware.vise.data.query.ResultItem;
import com.vmware.vise.data.query.ResultSet;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class VsanWitnessHostProvider implements DataProviderAdapter {
   public static final String PREFERRED_FD_PROPERTY = "preferredFaultDomain";
   public static final String UNICAST_AGENT_ADDRESS = "unicastAgentAddress";
   public static final String IS_WITNESS_HOST_PROPERTY = "isWitnessHost";
   private static final Log _logger = LogFactory.getLog(VsanWitnessHostProvider.class);
   private final DataServiceExtensionRegistry _registry;
   @Autowired
   private VsanStretchedClusterPropertyProvider stretchedClusterPropertyProvider;

   public VsanWitnessHostProvider(DataServiceExtensionRegistry registry) {
      this._registry = registry;
      this._registry.registerDataAdapter(this, new String[]{ClusterComputeResource.class.getSimpleName()});
   }

   public Response getData(RequestSpec requestSpec) {
      if (requestSpec != null && requestSpec.querySpec != null) {
         ArrayList<ResultSet> resultSets = new ArrayList(requestSpec.querySpec.length);
         QuerySpec[] var6;
         int var5 = (var6 = requestSpec.querySpec).length;

         for(int var4 = 0; var4 < var5; ++var4) {
            QuerySpec spec = var6[var4];
            ResultSet resultSet = new ResultSet();
            VsanWitnessHostProvider.RequestData requestData = this.getClusterRefs(spec);
            if (!requestData.clusterRefs.isEmpty()) {
               try {
                  resultSet = this.getHosts(requestData);
               } catch (Exception var10) {
                  _logger.error("Error retrieving witness hosts: ", var10);
                  resultSet.error = DataException.newInstance(var10);
               }
            }

            resultSets.add(resultSet);
         }

         Response response = new Response();
         response.resultSet = (ResultSet[])resultSets.toArray(new ResultSet[resultSets.size()]);
         return response;
      } else {
         throw new IllegalArgumentException("requestSpec");
      }
   }

   private VsanWitnessHostProvider.RequestData getClusterRefs(QuerySpec spec) {
      if (spec == null) {
         return new VsanWitnessHostProvider.RequestData();
      } else {
         ResourceSpec resourceSpec = spec.resourceSpec;
         if (resourceSpec == null) {
            return new VsanWitnessHostProvider.RequestData();
         } else {
            return resourceSpec.constraint == null ? new VsanWitnessHostProvider.RequestData() : this.getClusterRefs(resourceSpec.constraint);
         }
      }
   }

   private VsanWitnessHostProvider.RequestData getClusterRefs(Constraint constraint) {
      if (constraint instanceof RelationalConstraint) {
         RelationalConstraint relConstraint = (RelationalConstraint)constraint;
         if (("witnessHost".equals(relConstraint.relation) || "allVsanHosts".equals(relConstraint.relation)) && relConstraint.constraintOnRelatedObject instanceof ObjectIdentityConstraint) {
            ObjectIdentityConstraint oiConstraint = (ObjectIdentityConstraint)relConstraint.constraintOnRelatedObject;
            if (oiConstraint.target != null) {
               return new VsanWitnessHostProvider.RequestData(Arrays.asList((ManagedObjectReference)oiConstraint.target), "allVsanHosts".equals(relConstraint.relation));
            }
         }
      }

      return new VsanWitnessHostProvider.RequestData();
   }

   private ResultSet getHosts(VsanWitnessHostProvider.RequestData requestData) throws Exception {
      List<WitnessHostData> witnessHosts = this.getWitnessHosts(requestData.clusterRefs);
      List<ManagedObjectReference> regularHosts = this.getHostsInCluster(requestData);
      ResultItem[] resultItems = new ResultItem[witnessHosts.size() + regularHosts.size()];
      int index = 0;

      WitnessHostData witnessData;
      Iterator var7;
      for(var7 = witnessHosts.iterator(); var7.hasNext(); resultItems[index++] = this.createResultItem(witnessData.witnessHost, witnessData.preferredFaultDomainName, witnessData.unicastAgentAddress, true)) {
         witnessData = (WitnessHostData)var7.next();
      }

      ManagedObjectReference hostRef;
      for(var7 = regularHosts.iterator(); var7.hasNext(); resultItems[index++] = this.createResultItem(hostRef, "", "", false)) {
         hostRef = (ManagedObjectReference)var7.next();
      }

      return QueryUtil.newResultSet(resultItems);
   }

   private List<ManagedObjectReference> getHostsInCluster(VsanWitnessHostProvider.RequestData requestData) throws Exception {
      List<ManagedObjectReference> hosts = new ArrayList();
      if (requestData.isAllHostsRelation) {
         PropertyValue[] propValues = QueryUtil.getProperties((ManagedObjectReference[])requestData.clusterRefs.toArray(new ManagedObjectReference[0]), new String[]{"host"}).getPropertyValues();
         PropertyValue[] var7 = propValues;
         int var6 = propValues.length;

         for(int var5 = 0; var5 < var6; ++var5) {
            PropertyValue propValue = var7[var5];
            ManagedObjectReference[] clusterHosts = (ManagedObjectReference[])propValue.value;
            if (clusterHosts != null) {
               ManagedObjectReference[] var12 = clusterHosts;
               int var11 = clusterHosts.length;

               for(int var10 = 0; var10 < var11; ++var10) {
                  ManagedObjectReference hostRef = var12[var10];
                  hosts.add(hostRef);
               }
            }
         }
      }

      return hosts;
   }

   private List<WitnessHostData> getWitnessHosts(List<ManagedObjectReference> clusterRefs) {
      ArrayList allWitnessHosts = new ArrayList();

      try {
         Iterator var4 = clusterRefs.iterator();

         while(var4.hasNext()) {
            ManagedObjectReference clusterRef = (ManagedObjectReference)var4.next();
            List<WitnessHostData> witnessHosts = this.stretchedClusterPropertyProvider.getWitnessHosts(clusterRef);
            if (witnessHosts != null) {
               allWitnessHosts.addAll(witnessHosts);
            }
         }
      } catch (Exception var6) {
         _logger.error("Could not retrieve witness hosts", var6);
      }

      return allWitnessHosts;
   }

   private ResultItem createResultItem(ManagedObjectReference moRef, String preferredFd, String unicastAgentAddress, boolean isWitnessHost) {
      return QueryUtil.newResultItem(moRef, QueryUtil.newProperty("preferredFaultDomain", getNotNull(preferredFd)), QueryUtil.newProperty("unicastAgentAddress", getNotNull(unicastAgentAddress)), QueryUtil.newProperty("isWitnessHost", isWitnessHost));
   }

   private static String getNotNull(String value) {
      return value == null ? "" : value;
   }

   private class RequestData {
      public List<ManagedObjectReference> clusterRefs = new ArrayList();
      public boolean isAllHostsRelation = false;

      public RequestData(List<ManagedObjectReference> clusterRefs, boolean isAllHostsRelation) {
         this.clusterRefs = clusterRefs;
         this.isAllHostsRelation = isAllHostsRelation;
      }

      public RequestData() {
      }
   }
}
