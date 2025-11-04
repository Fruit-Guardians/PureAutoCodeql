package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc;

import com.vmware.vim.vmomi.client.http.ThumbprintVerifier;
import com.vmware.vim.vmomi.core.types.VmodlContext;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.ResourceFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.ClientCertificate;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http.HttpSettings;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http.LenientThumbprintVerifier;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.executor.CloseableExecutorService;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.executor.ExecutorSettings;
import java.util.Map;

public class SdkTunnelHttpSettings extends HttpSettings {
   public static final int PROXY_PORT = 80;
   public static final String TUNNEL_HOST = "sdkTunnel";
   public static final int TUNNEL_PORT = 8089;

   public SdkTunnelHttpSettings(String vcAddress, ResourceFactory<CloseableExecutorService, ExecutorSettings> executorMgr, ExecutorSettings executorSettings, Class<?> version, VmodlContext vmodlContext) {
      this(vcAddress, 80, executorMgr, executorSettings, version, vmodlContext);
   }

   public SdkTunnelHttpSettings(String vcAddress, int vcProxyPort, ResourceFactory<CloseableExecutorService, ExecutorSettings> executorMgr, ExecutorSettings executorSettings, Class<?> version, VmodlContext vmodlContext) {
      super("https", "sdkTunnel", 8089, (String)null, "http", vcAddress, vcProxyPort, -1, -1, (ClientCertificate)null, (ClientCertificate)null, new LenientThumbprintVerifier(), executorMgr, executorSettings, version, vmodlContext, (Map)null);
   }

   public SdkTunnelHttpSettings(String vcProxyHost, int vcProxyPort, int maxConn, int timeout, ThumbprintVerifier verifier, ResourceFactory<CloseableExecutorService, ExecutorSettings> executorMgr, ExecutorSettings executorSettings, Class<?> version, VmodlContext vmodlContext) {
      super("https", "sdkTunnel", 8089, (String)null, "http", vcProxyHost, vcProxyPort, maxConn, timeout, (ClientCertificate)null, (ClientCertificate)null, verifier, executorMgr, executorSettings, version, vmodlContext, (Map)null);
   }
}
