package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.tokenstore;

import com.vmware.vim.sso.client.ConfirmationType;
import com.vmware.vim.sso.client.SamlToken;
import com.vmware.vim.sso.client.TokenSpec;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.ResourceFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiSettings;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.LookupSvcConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.SsoAdminConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.StsService;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RenewingTokenRetriever extends AbstractTokenRetriever {
   private static Logger logger = LoggerFactory.getLogger(RenewingTokenRetriever.class);
   protected static final long ABOUT_TO_EXPIRE_MSEC = 3600000L;
   protected static final long EXTEND_LIFETIME_SEC = 86400L;
   protected static final long RENEW_RETRY_MSEC = 30000L;
   protected volatile SamlToken cachedToken;
   protected volatile boolean running;
   protected final ScheduledExecutorService scheduler;

   public RenewingTokenRetriever(PrivateKey privateKey, X509Certificate cert, VlsiSettings lsSettings, ResourceFactory<LookupSvcConnection, VlsiSettings> lsFactory, ResourceFactory<SsoAdminConnection, VlsiSettings> adminFactory, ScheduledExecutorService scheduler, SamlToken initialToken) {
      super(privateKey, cert, lsSettings, lsFactory, adminFactory);
      this.running = true;
      this.scheduler = scheduler;
      this.cachedToken = initialToken;
      this.scheduleRenewal(this.renewIn());
   }

   public RenewingTokenRetriever(PrivateKey privateKey, X509Certificate cert, VlsiSettings lsSettings, ResourceFactory<LookupSvcConnection, VlsiSettings> lsFactory, ResourceFactory<SsoAdminConnection, VlsiSettings> adminFactory, ScheduledExecutorService scheduler, String username, String password, TokenSpec tokenSpec) {
      this(privateKey, cert, lsSettings, lsFactory, adminFactory, scheduler, acquireToken(privateKey, cert, lsSettings, lsFactory, adminFactory, username, password, tokenSpec));
   }

   protected static SamlToken acquireToken(PrivateKey param0, X509Certificate param1, VlsiSettings param2, ResourceFactory<LookupSvcConnection, VlsiSettings> param3, ResourceFactory<SsoAdminConnection, VlsiSettings> param4, String param5, String param6, TokenSpec param7) {
      // $FF: Couldn't be decompiled
   }

   protected void scheduleRenewal(long renewIn) {
      if (!this.cachedToken.isRenewable()) {
         logger.warn("Not a renewable token: " + this.cachedToken.getSubject());
      } else if (this.cachedToken.getConfirmationType() != ConfirmationType.HOLDER_OF_KEY) {
         logger.warn("Non-HoK token cannot be renewed: " + this.cachedToken.getSubject());
      } else if (!this.running) {
         logger.debug("Retriever has been shut down, stopping renewal of token: {}", this.cachedToken.getSubject());
      } else {
         logger.debug("Scheduling token renewal for {} at {}.", this.cachedToken.getSubject(), new Date(System.currentTimeMillis() + renewIn));
         this.scheduler.schedule(new Runnable() {
            public void run() {
               if (RenewingTokenRetriever.this.running) {
                  try {
                     Throwable var1 = null;
                     Object var2 = null;

                     try {
                        LookupSvcConnection conn = (LookupSvcConnection)RenewingTokenRetriever.this.lsFactory.acquire(RenewingTokenRetriever.this.lsSettings);

                        try {
                           StsService sts = RenewingTokenRetriever.getSts(RenewingTokenRetriever.this.privateKey, RenewingTokenRetriever.this.certificate, conn, RenewingTokenRetriever.this.adminFactory, RenewingTokenRetriever.this.lsSettings);
                           RenewingTokenRetriever.this.cachedToken = sts.getStsClient().renewToken(RenewingTokenRetriever.this.cachedToken, 86400L);
                        } finally {
                           if (conn != null) {
                              conn.close();
                           }

                        }
                     } catch (Throwable var12) {
                        if (var1 == null) {
                           var1 = var12;
                        } else if (var1 != var12) {
                           var1.addSuppressed(var12);
                        }

                        throw var1;
                     }

                     if (RenewingTokenRetriever.logger.isInfoEnabled()) {
                        RenewingTokenRetriever.logger.debug("Successfully renewed token for {}.", RenewingTokenRetriever.this.cachedToken.getSubject());
                     }

                     RenewingTokenRetriever.this.scheduleRenewal(RenewingTokenRetriever.this.renewIn());
                  } catch (Exception var13) {
                     RenewingTokenRetriever.logger.warn("SAML Token Renewal Failure for {}", RenewingTokenRetriever.this.cachedToken.getSubject(), var13);
                     RenewingTokenRetriever.this.scheduleRenewal(30000L);
                  }

               }
            }
         }, renewIn, TimeUnit.MILLISECONDS);
      }
   }

   protected long renewIn() {
      long renewIn = this.cachedToken.getExpirationTime().getTime() - System.currentTimeMillis() - 3600000L;
      if (renewIn < 0L) {
         renewIn = 0L;
      }

      return renewIn;
   }

   public TokenInfo retrieveToken() {
      Date expiration = this.cachedToken.getExpirationTime();
      if (expiration.before(new Date())) {
         throw new TokenExpiredException("The token has expired: " + expiration);
      } else {
         return new TokenInfo(this.privateKey, this.cachedToken);
      }
   }

   public void shutdown() {
      logger.debug("Shutting down token retriever: {}", this);
      this.running = false;
   }

   public String toString() {
      return "RenewingTokenRetriever{cachedToken=" + this.cachedToken.getSubject() + "}";
   }
}
