package com.vmware.vsphere.client.vsan.util;

import com.vmware.vise.usersession.UserSessionService;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.ResourceBundle.Control;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public final class MessageBundle {
   private static final Logger logger = LoggerFactory.getLogger(MessageBundle.class);
   @Autowired
   private UserSessionService sessionService;
   private String bundlePath;

   public MessageBundle() {
      this("vsanservice");
   }

   public MessageBundle(String bundlePath) {
      this.bundlePath = bundlePath;
   }

   public String string(String key) {
      return this.string(key, (Object[])null);
   }

   public String string(String key, Object... parameters) {
      String formatString = this.loadResourceBundle().getString(key);
      if (parameters != null && parameters.length != 0) {
         formatString = formatString.replaceAll("'", "''");
         return MessageFormat.format(formatString, parameters);
      } else {
         return formatString;
      }
   }

   public String string(String key, String... parameters) {
      return this.string(key, (Object[])parameters);
   }

   private ResourceBundle loadResourceBundle() {
      try {
         return ResourceBundle.getBundle(this.bundlePath, this.getCurrentLocale(), this.getClass().getClassLoader(), new MessageBundle.UTF8Control((MessageBundle.UTF8Control)null));
      } catch (MissingResourceException var2) {
         throw new IllegalStateException("Cannot load module: " + this.bundlePath, var2);
      }
   }

   private Locale getCurrentLocale() {
      Locale locale = Locale.US;

      try {
         String languageTag = this.sessionService.getUserSession().locale.replaceAll("_", "-");
         locale = Locale.forLanguageTag(languageTag);
      } catch (Throwable var3) {
         logger.error("Cannot determine current locale, fallback to default: {}", locale, var3);
      }

      return locale;
   }

   private static class UTF8Control extends Control {
      private UTF8Control() {
      }

      public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload) throws IllegalAccessException, InstantiationException, IOException {
         String bundleName = this.toBundleName(baseName, locale);
         ResourceBundle bundle = null;
         if (format.equals("java.class")) {
            try {
               Class<? extends ResourceBundle> bundleClass = loader.loadClass(bundleName);
               if (!ResourceBundle.class.isAssignableFrom(bundleClass)) {
                  throw new ClassCastException(bundleClass.getName() + " cannot be cast to ResourceBundle");
               }

               bundle = (ResourceBundle)bundleClass.newInstance();
            } catch (ClassNotFoundException var18) {
            }
         } else {
            if (!format.equals("java.properties")) {
               throw new IllegalArgumentException("unknown format: " + format);
            }

            final String resourceName = this.toResourceName(bundleName, "properties");
            final ClassLoader classLoader = loader;
            final boolean reloadFlag = reload;
            InputStream stream = null;

            try {
               stream = (InputStream)AccessController.doPrivileged(new PrivilegedExceptionAction<InputStream>() {
                  public InputStream run() throws IOException {
                     InputStream is = null;
                     if (reloadFlag) {
                        URL url = classLoader.getResource(resourceName);
                        if (url != null) {
                           URLConnection connection = url.openConnection();
                           if (connection != null) {
                              connection.setUseCaches(false);
                              is = connection.getInputStream();
                           }
                        }
                     } else {
                        is = classLoader.getResourceAsStream(resourceName);
                     }

                     return is;
                  }
               });
            } catch (PrivilegedActionException var17) {
               throw (IOException)var17.getException();
            }

            if (stream != null) {
               try {
                  bundle = new PropertyResourceBundle(new InputStreamReader(stream, "UTF-8"));
               } finally {
                  stream.close();
               }
            }
         }

         return (ResourceBundle)bundle;
      }

      public Locale getFallbackLocale(String baseName, Locale locale) {
         return null;
      }

      // $FF: synthetic method
      UTF8Control(MessageBundle.UTF8Control var1) {
         this();
      }
   }
}
