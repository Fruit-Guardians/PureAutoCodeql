package com.vmware.vsphere.client.vsan.iscsi.adapter;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiInitiatorGroup;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTarget;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTargetBasicInfo;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTargetSystem;
import com.vmware.vise.data.Constraint;
import com.vmware.vise.data.query.CompositeConstraint;
import com.vmware.vise.data.query.DataProviderAdapter;
import com.vmware.vise.data.query.PropertyConstraint;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.RequestSpec;
import com.vmware.vise.data.query.Response;
import com.vmware.vise.data.query.ResultItem;
import com.vmware.vise.data.query.ResultSet;
import com.vmware.vise.data.query.type;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.iscsi.utils.VsanIscsiTargetUriUtil;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.commons.lang.Validate;

@type("VsanIscsiTarget")
public class VsanIscsiTargetDataAdapter implements DataProviderAdapter {
   private static final String VSAN_ISCSI_TARGET_URI_PREFIX = "urn:vsaniscsi:VsanIscsiTarget:VsanIscsiTargetList:NO#";
   private static final String VSAN_ISCSI_TARGET_CLUSTERREF_PROPERTY = "clusterRef";
   private static final String VSAN_ISCSI_TARGET_INITIATORGROUPIQN_PROPERTY = "initiatorGroupIqn";
   private static final String VSAN_ISCSI_TARGET_IQN_FIELD = "iqn";
   private static final String VSAN_ISCSI_TARGET_ALIAS_FIELD = "alias";
   private static final String VSAN_ISCSI_TARGET_AUTHTYPE_FIELD = "authType";
   private static final VsanProfiler _profiler = new VsanProfiler(VsanIscsiTargetDataAdapter.class);

   public Response getData(RequestSpec request) throws Exception {
      ManagedObjectReference clusterRef = null;
      String initiatorGroupIqn = null;
      Constraint constraint = request.querySpec[0].resourceSpec.constraint;
      if (constraint instanceof CompositeConstraint) {
         CompositeConstraint compositeConstraint = (CompositeConstraint)constraint;
         Constraint[] childConstraints = compositeConstraint.nestedConstraints;
         Constraint[] var10 = childConstraints;
         int var9 = childConstraints.length;

         for(int var8 = 0; var8 < var9; ++var8) {
            Constraint childConstraint = var10[var8];
            if (childConstraint instanceof PropertyConstraint) {
               PropertyConstraint propertyConstraint = (PropertyConstraint)childConstraint;
               if (propertyConstraint.propertyName.equals("clusterRef")) {
                  clusterRef = (ManagedObjectReference)propertyConstraint.comparableValue;
               } else if (propertyConstraint.propertyName.equals("initiatorGroupIqn")) {
                  initiatorGroupIqn = (String)propertyConstraint.comparableValue;
               }
            }
         }
      }

      Validate.notNull(clusterRef);
      Validate.notEmpty(initiatorGroupIqn);
      Response res = new Response();
      ResultSet rs = new ResultSet();
      ResultItem[] its = this.createResultItems(this.getTargetsNotInAccessibleList(clusterRef, initiatorGroupIqn));
      rs.items = its;
      rs.totalMatchedObjectCount = rs.items.length;
      ResultSet[] rss = new ResultSet[]{rs};
      res.resultSet = rss;
      return res;
   }

   private VsanIscsiTarget[] getTargetsNotInAccessibleList(ManagedObjectReference clusterRef, String initiatorGroupIqn) throws Exception {
      VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
      VsanIscsiTarget[] allTargets = null;

      Exception ex;
      try {
         Throwable var5 = null;
         ex = null;

         try {
            VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.getIscsiTargets");

            try {
               allTargets = vsanIscsiSystem.getIscsiTargets(clusterRef);
            } finally {
               if (p != null) {
                  p.close();
               }

            }
         } catch (Throwable var21) {
            if (var5 == null) {
               var5 = var21;
            } else if (var5 != var21) {
               var5.addSuppressed(var21);
            }

            throw var5;
         }
      } catch (Exception var22) {
         ex = new Exception(var22.getLocalizedMessage(), var22);
         throw ex;
      }

      if (allTargets == null) {
         return null;
      } else {
         VsanIscsiInitiatorGroup vsanIscsiInitiatorGroup = vsanIscsiSystem.getIscsiInitiatorGroup(clusterRef, initiatorGroupIqn);
         VsanIscsiTargetBasicInfo[] targetsInAccessibleList = vsanIscsiInitiatorGroup.getTargets();
         ArrayList<VsanIscsiTarget> allNotInAccessibleTargets = new ArrayList(Arrays.asList(allTargets));
         if (targetsInAccessibleList != null && targetsInAccessibleList.length > 0) {
            VsanIscsiTargetBasicInfo[] var11 = targetsInAccessibleList;
            int var10 = targetsInAccessibleList.length;

            for(int var9 = 0; var9 < var10; ++var9) {
               VsanIscsiTargetBasicInfo targetInAccessibleList = var11[var9];

               for(int i = allNotInAccessibleTargets.size() - 1; i >= 0; --i) {
                  VsanIscsiTarget unsureTarget = (VsanIscsiTarget)allNotInAccessibleTargets.get(i);
                  if (unsureTarget != null && unsureTarget.iqn.equals(targetInAccessibleList.iqn)) {
                     allNotInAccessibleTargets.remove(i);
                  }
               }
            }
         }

         return (VsanIscsiTarget[])allNotInAccessibleTargets.toArray(new VsanIscsiTarget[0]);
      }
   }

   private ResultItem[] createResultItems(VsanIscsiTarget[] targets) throws Exception {
      if (targets != null && targets.length != 0) {
         int targetsCount = targets.length;
         ResultItem[] its = new ResultItem[targetsCount];

         for(int i = 0; i < targetsCount; ++i) {
            VsanIscsiTarget target = targets[i];
            ResultItem it = new ResultItem();
            it.resourceObject = new URI("urn:vsaniscsi:VsanIscsiTarget:VsanIscsiTargetList:NO#" + VsanIscsiTargetUriUtil.encode(target.alias));
            it.properties = this.createPropertyValues(target);
            its[i] = it;
         }

         return its;
      } else {
         return new ResultItem[0];
      }
   }

   private PropertyValue[] createPropertyValues(VsanIscsiTarget target) throws Exception {
      PropertyValue iqn_pv = new PropertyValue();
      iqn_pv.propertyName = "iqn";
      iqn_pv.value = target.iqn;
      PropertyValue alias_pv = new PropertyValue();
      alias_pv.propertyName = "alias";
      alias_pv.value = target.alias;
      PropertyValue authType_pv = new PropertyValue();
      authType_pv.propertyName = "authType";
      authType_pv.value = target.authSpec.authType;
      PropertyValue[] pvs = new PropertyValue[]{iqn_pv, alias_pv, authType_pv};
      return pvs;
   }
}
