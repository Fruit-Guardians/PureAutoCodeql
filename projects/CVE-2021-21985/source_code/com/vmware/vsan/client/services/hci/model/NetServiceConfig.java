package com.vmware.vsan.client.services.hci.model;

import com.vmware.vim.binding.vim.ClusterComputeResource.HostVmkNicInfo;
import com.vmware.vim.binding.vim.host.IpConfig;
import com.vmware.vim.binding.vim.host.IpRouteConfig;
import com.vmware.vim.binding.vim.host.IpConfig.IpV6Address;
import com.vmware.vim.binding.vim.host.IpConfig.IpV6AddressConfiguration;
import com.vmware.vim.binding.vim.host.VirtualNic.IpRouteSpec;
import com.vmware.vim.binding.vim.host.VirtualNic.Specification;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;

@data
public class NetServiceConfig {
   public Service service;
   public boolean useVlan;
   public int vlan;
   public String dvpgName;
   public ManagedObjectReference existingDvpgMor;
   public NetServiceConfig.Protocol protocol;
   public NetServiceConfig.IpType ipv4IpType;
   public NetServiceConfig.HostIpv4Config[] hostIpv4Configs;
   public NetServiceConfig.IpType ipv6IpType;
   public NetServiceConfig.HostIpv6Config[] hostIpv6Configs;
   // $FF: synthetic field
   private static int[] $SWITCH_TABLE$com$vmware$vsan$client$services$hci$model$NetServiceConfig$IpType;

   public HostVmkNicInfo getHostVmkNicInfo(String hostName) {
      HostVmkNicInfo result = new HostVmkNicInfo();
      result.service = this.service.getText();
      result.nicSpec = new Specification();
      result.nicSpec.ip = this.getIpConfig(hostName);
      result.nicSpec.ipRouteSpec = this.getIpRouteSpec(hostName);
      return result;
   }

   private IpConfig getIpConfig(String hostName) {
      IpConfig result = new IpConfig();
      if (this.protocol == NetServiceConfig.Protocol.IPV4 || this.protocol == NetServiceConfig.Protocol.MIXED) {
         result.dhcp = this.ipv4IpType == NetServiceConfig.IpType.DHCP;
         if (!result.dhcp) {
            NetServiceConfig.HostIpv4Config ipv4Config = this.getHostIpv4Config(hostName);
            result.ipAddress = ipv4Config.ipAddress;
            result.subnetMask = ipv4Config.subnetMask;
         }
      }

      if (this.protocol == NetServiceConfig.Protocol.IPV6 || this.protocol == NetServiceConfig.Protocol.MIXED) {
         result.ipV6Config = new IpV6AddressConfiguration();
         switch($SWITCH_TABLE$com$vmware$vsan$client$services$hci$model$NetServiceConfig$IpType()[this.ipv6IpType.ordinal()]) {
         case 1:
            result.ipV6Config.ipV6Address = new IpV6Address[]{new IpV6Address()};
            result.ipV6Config.autoConfigurationEnabled = false;
            IpV6Address ipv6Address = result.ipV6Config.ipV6Address[0];
            NetServiceConfig.HostIpv6Config ipv6Config = this.getHostIpv6Config(hostName);
            ipv6Address.ipAddress = ipv6Config.ipAddress;
            ipv6Address.prefixLength = ipv6Config.prefixLength;
            ipv6Address.operation = "add";
            break;
         case 2:
            result.ipV6Config.dhcpV6Enabled = true;
            break;
         case 3:
            result.ipV6Config.autoConfigurationEnabled = true;
         }
      }

      return result;
   }

   private NetServiceConfig.HostIpv4Config getHostIpv4Config(String hostName) {
      if (this.hostIpv4Configs != null) {
         NetServiceConfig.HostIpv4Config[] var5;
         int var4 = (var5 = this.hostIpv4Configs).length;

         for(int var3 = 0; var3 < var4; ++var3) {
            NetServiceConfig.HostIpv4Config config = var5[var3];
            if (config.hostname.equals(hostName)) {
               return config;
            }
         }
      }

      return null;
   }

   private NetServiceConfig.HostIpv6Config getHostIpv6Config(String hostName) {
      if (this.hostIpv6Configs != null) {
         NetServiceConfig.HostIpv6Config[] var5;
         int var4 = (var5 = this.hostIpv6Configs).length;

         for(int var3 = 0; var3 < var4; ++var3) {
            NetServiceConfig.HostIpv6Config config = var5[var3];
            if (config.hostname.equals(hostName)) {
               return config;
            }
         }
      }

      return null;
   }

   private IpRouteSpec getIpRouteSpec(String hostName) {
      IpRouteSpec result = new IpRouteSpec();
      result.ipRouteConfig = new IpRouteConfig();
      if ((this.protocol == NetServiceConfig.Protocol.IPV4 || this.protocol == NetServiceConfig.Protocol.MIXED) && this.ipv4IpType != NetServiceConfig.IpType.DHCP) {
         result.ipRouteConfig.defaultGateway = this.getHostIpv4Config(hostName).defaultGateway;
      }

      if ((this.protocol == NetServiceConfig.Protocol.IPV6 || this.protocol == NetServiceConfig.Protocol.MIXED) && this.ipv6IpType == NetServiceConfig.IpType.STATIC) {
         result.ipRouteConfig.ipV6DefaultGateway = this.getHostIpv6Config(hostName).defaultGateway;
      }

      return result;
   }

   // $FF: synthetic method
   static int[] $SWITCH_TABLE$com$vmware$vsan$client$services$hci$model$NetServiceConfig$IpType() {
      int[] var10000 = $SWITCH_TABLE$com$vmware$vsan$client$services$hci$model$NetServiceConfig$IpType;
      if (var10000 != null) {
         return var10000;
      } else {
         int[] var0 = new int[NetServiceConfig.IpType.values().length];

         try {
            var0[NetServiceConfig.IpType.DHCP.ordinal()] = 2;
         } catch (NoSuchFieldError var3) {
         }

         try {
            var0[NetServiceConfig.IpType.ROUTER_ADVERTISEMENT.ordinal()] = 3;
         } catch (NoSuchFieldError var2) {
         }

         try {
            var0[NetServiceConfig.IpType.STATIC.ordinal()] = 1;
         } catch (NoSuchFieldError var1) {
         }

         $SWITCH_TABLE$com$vmware$vsan$client$services$hci$model$NetServiceConfig$IpType = var0;
         return var0;
      }
   }

   @data
   public static class HostIpv4Config {
      public String hostname;
      public String ipAddress;
      public String subnetMask;
      public String defaultGateway;
   }

   @data
   public static class HostIpv6Config {
      public String hostname;
      public String ipAddress;
      public int prefixLength;
      public String defaultGateway;
   }

   @data
   public static enum IpType {
      STATIC,
      DHCP,
      ROUTER_ADVERTISEMENT;
   }

   @data
   public static enum Protocol {
      IPV4,
      IPV6,
      MIXED;
   }
}
