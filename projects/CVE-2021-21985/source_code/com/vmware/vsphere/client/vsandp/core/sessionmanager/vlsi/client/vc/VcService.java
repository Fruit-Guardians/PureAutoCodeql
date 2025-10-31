package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc;

import com.vmware.vise.usersession.ServerInfo;
import com.vmware.vise.usersession.UserSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VcService {
   @Autowired
   public UserSessionService sessionService;

   public ServerInfo findServerInfo(String vcUuid) {
      if (vcUuid == null) {
         throw new IllegalArgumentException("vcUuid cannot be null, probably coming from MOR without serverGuid.");
      } else {
         ServerInfo[] var5;
         int var4 = (var5 = this.sessionService.getUserSession().serversInfo).length;

         for(int var3 = 0; var3 < var4; ++var3) {
            ServerInfo vcServer = var5[var3];
            if (vcUuid.equalsIgnoreCase(vcServer.serviceGuid)) {
               return vcServer;
            }
         }

         throw new IllegalStateException("Not found server info for: " + vcUuid);
      }
   }
}
