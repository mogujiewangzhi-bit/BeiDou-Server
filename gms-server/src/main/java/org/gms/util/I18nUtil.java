package org.gms.util;

import org.gms.constants.string.CharsetConstants;
import org.gms.manager.ServerManager;
import org.gms.property.ServiceProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Arrays;
import java.util.Locale;

public class I18nUtil {
    public static final Locale LANGUAGE = serviceLanguage();
    public static final MessageSource messageSource = new DeferredMessageSource("messageSource", "i18n/message");
    public static final MessageSource logSource = new DeferredMessageSource("logSource", "i18n/log");
    public static final MessageSource exceptionSource = new DeferredMessageSource("exceptionSource", "i18n/exception");

    private static Locale serviceLanguage() {
        ApplicationContext context = ServerManager.getApplicationContext();
        return context == null ? Locale.SIMPLIFIED_CHINESE
                : Locale.forLanguageTag(context.getBean(ServiceProperty.class).getLanguage());
    }

    // Offline tools must not initialize Spring or pin a missing application context.
    private static final class DeferredMessageSource implements MessageSource {
        private final String beanName;
        private final ResourceBundleMessageSource fallback = new ResourceBundleMessageSource();

        private DeferredMessageSource(String beanName, String basename) {
            this.beanName = beanName;
            fallback.setBasename(basename);
            fallback.setDefaultEncoding("UTF-8");
        }

        private MessageSource delegate() {
            ApplicationContext context = ServerManager.getApplicationContext();
            return context == null ? fallback : context.getBean(beanName, MessageSource.class);
        }

        @Override
        public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
            return delegate().getMessage(code, args, defaultMessage, locale);
        }

        @Override
        public String getMessage(String code, Object[] args, Locale locale) {
            return delegate().getMessage(code, args, locale);
        }

        @Override
        public String getMessage(MessageSourceResolvable resolvable, Locale locale) {
            return delegate().getMessage(resolvable, locale);
        }
    }

    public static String getMessage(String code, Object... args) {
        Locale clientLang = ServerManager.getApplicationContext() == null ? serviceLanguage()
                : CharsetConstants.getLanguageLocale(ThreadLocalUtil.getClientLang());
        String[] stringArgs = Arrays.stream(args).map(String::valueOf).toArray(String[]::new);
        return messageSource.getMessage(code, stringArgs, clientLang);
    }

    public static String getMessage(Locale locale, String code, Object... args) {
        return messageSource.getMessage(code, args, locale);
    }

    public static String getLogMessage(String code, Object... args) {
        return logSource.getMessage(code, args, serviceLanguage());
    }

    public static String getLogMessage(Locale locale, String code, Object... args) {
        return logSource.getMessage(code, args, locale);
    }

    public static String getExceptionMessage(String code, Object... args) {
        return exceptionSource.getMessage(code, args, serviceLanguage());
    }

    public static String getExceptionMessage(Locale locale, String code, Object... args) {
        return exceptionSource.getMessage(code, args, locale);
    }
}
