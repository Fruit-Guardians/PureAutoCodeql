package com.vmware.vsan.client.services.dataprotection.model;

import com.vmware.vise.core.model.data;
import java.util.SortedSet;
import java.util.TreeSet;

@data
public class ProtectionsMonitorData {
   public int cgErrorsCount;
   public SortedSet<ProtectionItem> protectionItems = new TreeSet();

   public String toString() {
      return String.format("%s [cgErrorsCount = %d, protectionItems (count=%d)]", this.getClass().getName(), this.cgErrorsCount, this.protectionItems.size());
   }
}
