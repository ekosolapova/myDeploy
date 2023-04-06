/* Copyright © 2022 EIS Group and/or one of its affiliates. All rights reserved. Unpublished work under U.S. copyright laws.
 CONFIDENTIAL AND TRADE SECRET INFORMATION. No portion of this work may be copied, distributed, modified, or incorporated into any other media without EIS Group prior written consent.*/

import org.openl.rules.ruleservice.core.annotations.ExternalParam
import org.openl.rules.ruleservice.core.interceptors.RulesType
import org.openl.rules.ruleservice.core.interceptors.annotations.ServiceCallBeforeInterceptor

import javax.ws.rs.HeaderParam
import javax.ws.rs.core.HttpHeaders

/**
 * Offer API service enhancer.
 * To enable it, do the following:
 * 1. The following property must be added into the rules-deploy.xml of master-offer project:
 * <pre>
 * {@code <annotationTemplateClassName>OfferApiService</annotationTemplateClassName>
 *}
 * </pre>
 * 2. Remove Behavior table from rules.xml file.
 *
 * @since 22.16
 * @author Vladyslav Pikus
 */
interface OfferApiService {

    @ServiceCallBeforeInterceptor(BehaviourRequestLocaleProvider.class)
    @RulesType("BehaviorResponse")
    Object Behavior(@RulesType("BehaviorRequest") Object behaviorRequest, @ExternalParam @HeaderParam(HttpHeaders.ACCEPT_LANGUAGE) String langs);

}
