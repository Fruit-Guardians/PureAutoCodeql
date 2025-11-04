package com.vmware.vsan.client.services.dataprotection.model;

public abstract class ProtectionItem implements Comparable<ProtectionItem> {
   public abstract String getName();

   public int compareTo(ProtectionItem o) {
      return this.getName().compareTo(o.getName());
   }
}
