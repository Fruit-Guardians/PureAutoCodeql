package com.vmware.vsan.client.services.virtualobjects.data;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;
import com.vmware.vsphere.client.vsan.base.data.VsanObjectDataProtectionHealthState;
import com.vmware.vsphere.client.vsan.base.data.VsanObjectHealthState;
import com.vmware.vsphere.client.vsan.base.data.VsanObjectType;
import com.vmware.vsphere.client.vsan.whatif.VsanWhatIfComplianceStatus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.ObjectUtils;

@data
public class VirtualObjectModel {
   public String uid;
   public ManagedObjectReference vmRef;
   public String iconId;
   public String name;
   public VirtualObjectsFilter applicableFilter;
   public VsanObjectHealthState healthState;
   public VsanWhatIfComplianceStatus whatIfComplianceStatus;
   public VsanObjectDataProtectionHealthState dataProtectionHealthState;
   public String storagePolicy;
   public VsanObjectType objectType;
   public VirtualObjectModel[] children;
   public static final Comparator<VirtualObjectModel> COMPARATOR = new Comparator<VirtualObjectModel>() {
      public int compare(VirtualObjectModel o1, VirtualObjectModel o2) {
         return o1.name.compareTo(o2.name);
      }
   };

   public VirtualObjectModel() {
      this.applicableFilter = VirtualObjectsFilter.OTHERS;
   }

   public VirtualObjectModel cloneWithoutChildren() {
      VirtualObjectModel o = new VirtualObjectModel();
      o.uid = this.uid;
      o.vmRef = this.vmRef;
      o.name = this.name;
      o.iconId = this.iconId;
      o.healthState = this.healthState;
      o.whatIfComplianceStatus = this.whatIfComplianceStatus;
      o.dataProtectionHealthState = this.dataProtectionHealthState;
      o.storagePolicy = this.storagePolicy;
      o.objectType = this.objectType;
      o.children = new VirtualObjectModel[0];
      return o;
   }

   public VirtualObjectModel cloneWithChildren() {
      VirtualObjectModel o = this.cloneWithoutChildren();
      List<VirtualObjectModel> children = new ArrayList(this.children.length);
      VirtualObjectModel[] var6;
      int var5 = (var6 = this.children).length;

      for(int var4 = 0; var4 < var5; ++var4) {
         VirtualObjectModel child = var6[var4];
         children.add(child.cloneWithoutChildren());
      }

      o.children = (VirtualObjectModel[])children.toArray(new VirtualObjectModel[children.size()]);
      return o;
   }

   public void mergeChildren(VirtualObjectModel clone) {
      if (clone != null && !ArrayUtils.isEmpty(clone.children)) {
         Set<VirtualObjectModel> children = new HashSet(Arrays.asList(this.children));
         VirtualObjectModel[] var6;
         int var5 = (var6 = clone.children).length;

         for(int var4 = 0; var4 < var5; ++var4) {
            VirtualObjectModel child = var6[var4];
            children.add(child);
         }

         this.children = (VirtualObjectModel[])children.toArray(new VirtualObjectModel[children.size()]);
      }
   }

   public boolean isOtherType() {
      if (this.vmRef != null) {
         return false;
      } else {
         return this.objectType != VsanObjectType.iscsiLun && this.objectType != VsanObjectType.iscsiTarget && this.objectType != VsanObjectType.improvedVirtualDisk && this.objectType != VsanObjectType.fileShare && this.objectType != VsanObjectType.improvedVirtualDisk && this.objectType != VsanObjectType.detachedCnsVolBlock && this.objectType != VsanObjectType.detachedCnsVolFile;
      }
   }

   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (!(o instanceof VirtualObjectModel)) {
         return false;
      } else {
         VirtualObjectModel that = (VirtualObjectModel)o;
         if (!ObjectUtils.equals(this.vmRef, that.vmRef)) {
            return false;
         } else {
            return this.uid == null ? Objects.equals(this.name, that.name) : Objects.equals(this.uid, that.uid);
         }
      }
   }

   public int hashCode() {
      return this.uid == null ? Objects.hash(new Object[]{this.name}) : Objects.hash(new Object[]{this.uid});
   }
}
