/* Copyright © 2022 EIS Group and/or one of its affiliates. All rights reserved. Unpublished work under U.S. copyright laws.
 CONFIDENTIAL AND TRADE SECRET INFORMATION. No portion of this work may be copied, distributed, modified, or incorporated into any other media without EIS Group prior written consent.*/

import org.openl.rules.lang.xls.binding.XlsModuleOpenClass
import org.openl.rules.lang.xls.types.DatatypeOpenField
import org.openl.rules.ruleservice.core.interceptors.InjectOpenClass
import org.openl.rules.ruleservice.core.interceptors.ServiceMethodBeforeAdvice
import org.openl.rules.vm.SimpleRulesVM
import org.openl.types.IOpenClass
import org.openl.types.IOpenField
import org.openl.types.java.JavaOpenClass
import org.openl.vm.IRuntimeEnv
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.lang.reflect.Method

/**
 * Injects 'Accept-Language' header to {@link org.openl.rules.context.IRulesRuntimeContext#setLocale(java.util.Locale)}
 *
 * @since 22.16
 * @author Vladyslav Pikus
 */
class BehaviourRequestLocaleProvider implements ServiceMethodBeforeAdvice {

    private static final Logger LOG = LoggerFactory.getLogger(BehaviourRequestLocaleProvider.class);

    private XlsModuleOpenClass rootOpenClass;

    void before(Method interfaceMethod, Object serviceBean, Object... args) throws Throwable {
        List<Locale> acceptLanguages = getAcceptableLanguages((String) args[1])
        if (acceptLanguages == null || acceptLanguages.isEmpty()) {
            LOG.warn("Unable to find 'Accept-Language' header argument. Language injection will be skipped.")
            return;
        }

        IOpenClass behaviorRequestOpenClass = rootOpenClass.findType("BehaviorRequest");
        Object behaviourRequest = args[0]

        IRuntimeEnv env = new SimpleRulesVM().getRuntimeEnv()
        for (IOpenField openField : behaviorRequestOpenClass.getFields()) {
            if (openField instanceof DatatypeOpenField) {
                DatatypeOpenField dtOpenField = (DatatypeOpenField) openField;
                if (dtOpenField.isContextProperty() && dtOpenField.getType() == JavaOpenClass.getOpenClass(Locale.class)) {
                    openField.set(behaviourRequest, acceptLanguages.get(0), env);
                }
            }
        }
    }

    private static List<Locale> getAcceptableLanguages(String value) {
        def langs = [] as ArrayList<Locale>
        if (value != null && !value.isBlank()) {
            for (String part : value.split(",")) {
                if (part.isBlank()) {
                    continue
                }
                String[] pair = part.split(";")
                Locale locale = getLocale(pair[0].trim());
                langs.add(locale)
            }
        }
        return langs
    }

    private static Locale getLocale(String value) {
        if (value == null) {
            return null
        }
        final String language
        String locale = null
        int index = value.indexOf('-')
        if (index == 0 || index == value.length() - 1) {
            throw new IllegalArgumentException("Illegal locale value : " + value);
        }

        if (index > 0) {
            language = value.substring(0, index)
            locale = value.substring(index + 1)
        } else {
            language = value
        }

        if (locale == null) {
            return new Locale(language)
        }
        return new Locale(language, locale);

    }


    @InjectOpenClass
    void setIOpenClass(IOpenClass openClass) {
        this.rootOpenClass = (XlsModuleOpenClass) openClass;
    }
}
