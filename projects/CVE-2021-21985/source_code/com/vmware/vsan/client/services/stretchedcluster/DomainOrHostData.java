package com.vmware.vsan.client.services.stretchedcluster;

import com.vmware.vise.core.model.data;
import com.vmware.vsan.client.services.common.data.ConnectionState;
import java.util.List;

@data
public class DomainOrHostData {
   public String uid;
   public String label;
   public boolean isHost;
   public String iconId;
   public boolean isPreferred;
   public boolean inMaintenanceMode;
   public ConnectionState connectionState;
   public DomainOrHostData[] children;

   public static DomainOrHostData createHostData(String uid, String label, String iconId, boolean inMaintenanceMode, ConnectionState connectionState) {
      DomainOrHostData result = new DomainOrHostData();
      result.uid = uid;
      result.label = label;
      result.isHost = true;
      result.iconId = iconId;
      result.inMaintenanceMode = inMaintenanceMode;
      result.connectionState = connectionState;
      result.isPreferred = false;
      return result;
   }

   public static DomainOrHostData createDomainData(String uid, String label, boolean isPreferred, List<DomainOrHostData> children) {
      DomainOrHostData result = new DomainOrHostData();
      result.uid = uid;
      result.label = label;
      result.isHost = false;
      result.children = (DomainOrHostData[])children.toArray(new DomainOrHostData[children.size()]);
      result.iconId = "vsan-fault-domain";
      result.isPreferred = isPreferred;
      return result;
   }

   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         DomainOrHostData that = (DomainOrHostData)o;
         return this.uid.equals(that.uid);
      } else {
         return false;
      }
   }

   public int hashCode() {
      return this.uid.hashCode();
   }
}
