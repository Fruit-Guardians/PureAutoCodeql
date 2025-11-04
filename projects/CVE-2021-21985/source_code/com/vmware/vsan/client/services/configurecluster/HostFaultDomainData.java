package com.vmware.vsan.client.services.configurecluster;

import com.vmware.vise.core.model.data;
import com.vmware.vsan.client.services.common.data.ConnectionState;
import org.apache.commons.lang.StringUtils;

@data
public class HostFaultDomainData {
   public String hostUid;
   public String name;
   public String primaryIconId;
   public String version;
   public String faultDomainName;
   public ConnectionState connectionState;
   public boolean canChangeFaultDomain;
   public boolean isFaultDomainSupported;
   public boolean hasEditPrivileges = true;

   public static HostFaultDomainData createHostFaultDomainData(String hostUid, String name, String primaryIconId, ConnectionState connectionState, String faultDomainName, String version) {
      HostFaultDomainData result = new HostFaultDomainData();
      result.hostUid = hostUid;
      result.name = name;
      result.primaryIconId = primaryIconId;
      result.connectionState = connectionState;
      result.faultDomainName = faultDomainName;
      result.version = version;
      result.canChangeFaultDomain = canChangeFaultDomain(connectionState, result.hasEditPrivileges, version);
      result.isFaultDomainSupported = isFaultDomainSupported(version);
      return result;
   }

   private static boolean canChangeFaultDomain(ConnectionState connectionState, boolean hasEditPrivileges, String version) {
      boolean isHostConnected = connectionState == ConnectionState.connected;
      return isFaultDomainSupported(version) && isHostConnected && hasEditPrivileges;
   }

   private static boolean isFaultDomainSupported(String version) {
      if (StringUtils.isEmpty(version)) {
         return false;
      } else {
         String[] versionNumbers = version.split("\\.");
         return version.equals("e.x.p") || versionNumbers.length > 0 && StringUtils.isNotEmpty(versionNumbers[0]) && Integer.parseInt(versionNumbers[0]) > 5;
      }
   }

   public static class Builder {
      private String hostUid;
      private String name;
      private String primaryIconId;
      private String version;
      private String faultDomainName;
      private ConnectionState connectionState;

      public HostFaultDomainData.Builder hostUid(String hostUid) {
         this.hostUid = hostUid;
         return this;
      }

      public HostFaultDomainData.Builder name(String name) {
         this.name = name;
         return this;
      }

      public HostFaultDomainData.Builder primaryIconId(String primaryIconId) {
         this.primaryIconId = primaryIconId;
         return this;
      }

      public HostFaultDomainData.Builder version(String version) {
         this.version = version;
         return this;
      }

      public HostFaultDomainData.Builder faultDomainName(String faultDomainName) {
         this.faultDomainName = faultDomainName;
         return this;
      }

      public HostFaultDomainData.Builder connectionState(ConnectionState connectionState) {
         this.connectionState = connectionState;
         return this;
      }

      public HostFaultDomainData createHostFaultDomainData() {
         return HostFaultDomainData.createHostFaultDomainData(this.hostUid, this.name, this.primaryIconId, this.connectionState, this.faultDomainName, this.version);
      }
   }
}
