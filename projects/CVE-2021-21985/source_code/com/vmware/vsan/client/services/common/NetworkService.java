package com.vmware.vsan.client.services.common;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vsan.client.services.common.data.IpAddressesRequestSpec;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.net.util.SubnetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NetworkService {
   private Logger logger = LoggerFactory.getLogger(NetworkService.class);

   @TsService
   public List<String> getConsequentHostAddresses(IpAddressesRequestSpec ipsRequestSpec) {
      SubnetUtils subnetUtils = new SubnetUtils(ipsRequestSpec.ipAddress, ipsRequestSpec.subnetMask);
      SubnetUtils.SubnetInfo subnetInfo = subnetUtils.getInfo();
      long addressCount = (long)(ipsRequestSpec.hostsNumber - 1);
      int initialAddressInt = subnetInfo.asInteger(ipsRequestSpec.ipAddress) + 1;
      int highestAddressInt = subnetInfo.asInteger(subnetInfo.getHighAddress());
      List<String> result = new ArrayList();
      int currentAddressInt = initialAddressInt;

      for(int idx = 0; currentAddressInt <= highestAddressInt && (long)idx < addressCount; ++idx) {
         result.add(this.formatIpv4Address(this.ipv4IntToArray(currentAddressInt)));
         ++currentAddressInt;
      }

      return result;
   }

   private int[] ipv4IntToArray(int value) {
      int[] result = new int[4];

      for(int j = 3; j >= 0; --j) {
         result[j] |= value >>> 8 * (3 - j) & 255;
      }

      return result;
   }

   private String formatIpv4Address(int[] octets) {
      StringBuilder result = new StringBuilder();

      for(int i = 0; i < octets.length; ++i) {
         result.append(octets[i]);
         if (i != octets.length - 1) {
            result.append(".");
         }
      }

      return result.toString();
   }
}
