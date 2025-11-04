package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.tokenstore;

public interface TokenRetriever {
   TokenInfo retrieveToken();

   TokenInfo retrieveDelegatedToken(String var1);

   void shutdown();
}
