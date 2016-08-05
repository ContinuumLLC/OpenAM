/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.openam.scripting.guice;

import static org.forgerock.openam.scripting.ScriptConstants.AUTHENTICATION_SERVER_SIDE_NAME;
import static org.forgerock.openam.scripting.ScriptConstants.OIDC_CLAIMS_NAME;
import static org.forgerock.openam.scripting.ScriptConstants.POLICY_CONDITION_NAME;
import static org.forgerock.openam.scripting.ScriptConstants.SCRIPTING_HTTP_CLIENT_NAME;
import static org.forgerock.openam.scripting.ScriptConstants.ScriptContext.AUTHENTICATION_SERVER_SIDE;
import static org.forgerock.openam.scripting.ScriptConstants.ScriptContext.OIDC_CLAIMS;
import static org.forgerock.openam.scripting.ScriptConstants.ScriptContext.POLICY_CONDITION;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.forgerock.guice.core.GuiceModule;
import org.forgerock.http.Client;
import org.forgerock.http.client.RestletHttpClient;
import org.forgerock.openam.scripting.ScriptConstants;
import org.forgerock.openam.scripting.common.ScriptEngineConfiguration;
import org.forgerock.openam.scripting.common.ScriptEvaluator;
import org.forgerock.openam.scripting.common.ScriptValidator;
import org.forgerock.openam.scripting.common.StandardScriptEngineManager;
import org.forgerock.openam.scripting.common.StandardScriptEvaluator;
import org.forgerock.openam.scripting.common.StandardScriptValidator;
import org.forgerock.openam.scripting.SupportedScriptingLanguage;
import org.forgerock.openam.scripting.ThreadPoolScriptEvaluator;
import org.forgerock.openam.scripting.api.http.GroovyHttpClient;
import org.forgerock.openam.scripting.api.http.JavaScriptHttpClient;
import org.forgerock.openam.shared.concurrency.ResizableLinkedBlockingQueue;
import org.forgerock.openam.shared.guice.CloseableHttpClientProvider;
import org.forgerock.util.thread.ExecutorServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.name.Names;


/**
 * Guice configuration for OpenAM scripting-related components.
 */
@GuiceModule
public class ScriptingGuiceModule extends AbstractModule {

    private final Logger logger = LoggerFactory.getLogger(ScriptConstants.LOGGER_NAME);

    @Override
    protected void configure() {
        bind(ScriptValidator.class).to(StandardScriptValidator.class);

        bind(Logger.class).annotatedWith(Names.named("ScriptLogger"))
                .toInstance(logger);

        bind(StandardScriptEngineManager.class)
                .annotatedWith(Names.named(AUTHENTICATION_SERVER_SIDE.name()))
                .toInstance(new StandardScriptEngineManager());

        bind(StandardScriptEngineManager.class)
                .annotatedWith(Names.named(POLICY_CONDITION.name()))
                .toInstance(new StandardScriptEngineManager());

        bind(StandardScriptEngineManager.class)
                .annotatedWith(Names.named(OIDC_CLAIMS.name()))
                .toInstance(new StandardScriptEngineManager());

        bind(RestletHttpClient.class)
                .annotatedWith(Names.named(SupportedScriptingLanguage.JAVASCRIPT.name()))
                .to(JavaScriptHttpClient.class);

        bind(RestletHttpClient.class)
                .annotatedWith(Names.named(SupportedScriptingLanguage.GROOVY.name()))
                .to(GroovyHttpClient.class);

        bind(Client.class)
                .annotatedWith(Names.named(SCRIPTING_HTTP_CLIENT_NAME))
                .toProvider(CloseableHttpClientProvider.class).in(Scopes.SINGLETON);
    }

    /**
     * Creates the script evaluator to use for evaluating scripted auth module scripts. The evaluator returned uses a
     * thread pool to evaluate scripts (supporting script interruption), delegating to a sandboxed script evaluator.
     *
     * @param scriptEngineManager the script engine manager to use.
     * @param executorServiceFactory the factory for creating managed thread pools for script execution.
     * @return an appropriately configured script evaluator for use with scripted authentication.
     */
    @Provides
    @Singleton
    @Inject
    @Named(AUTHENTICATION_SERVER_SIDE_NAME)
    ScriptEvaluator getAuthenticationServerSideScriptEvaluator(
            @Named(AUTHENTICATION_SERVER_SIDE_NAME) StandardScriptEngineManager scriptEngineManager,
            ExecutorServiceFactory executorServiceFactory) {

        return createEvaluator(scriptEngineManager, executorServiceFactory);
    }

    /**
     * Creates the script evaluator to use for evaluating entitlement condition scripts. The evaluator returned uses a
     * thread pool to evaluate scripts (supporting script interruption), delegating to a sandboxed script evaluator.
     *
     * @param scriptEngineManager the script engine manager to use.
     * @param executorServiceFactory the factory for creating managed thread pools for script execution.
     * @return an appropriately configured script evaluator for use with scripted entitlement condition.
     */
    @Provides
    @Singleton
    @Inject
    @Named(POLICY_CONDITION_NAME)
    ScriptEvaluator getPoliyConditionScriptEvaluator(
            @Named(POLICY_CONDITION_NAME) StandardScriptEngineManager scriptEngineManager,
            ExecutorServiceFactory executorServiceFactory) {

        return createEvaluator(scriptEngineManager, executorServiceFactory);
    }

    /**
     * Creates the script evaluator to use for evaluating OIDC Claims scripts. The evaluator returned uses a
     * thread pool to evaluate scripts (supporting script interruption), delegating to a sandboxed script evaluator.
     *
     * @param scriptEngineManager the script engine manager to use.
     * @param executorServiceFactory the factory for creating managed thread pools for script execution.
     * @return an appropriately configured script evaluator for use with OIDC Claims scripts.
     */
    @Provides
    @Singleton
    @Inject
    @Named(OIDC_CLAIMS_NAME)
    ScriptEvaluator getOidcClaimsScriptEvaluator(
            @Named(OIDC_CLAIMS_NAME) StandardScriptEngineManager scriptEngineManager,
            ExecutorServiceFactory executorServiceFactory) {

        return createEvaluator(scriptEngineManager, executorServiceFactory);
    }

    private ThreadPoolScriptEvaluator createEvaluator(StandardScriptEngineManager scriptEngineManager,
                                                      ExecutorServiceFactory executorServiceFactory) {

        ScriptEngineConfiguration configuration = scriptEngineManager.getConfiguration();

        return new ThreadPoolScriptEvaluator(scriptEngineManager,
                executorServiceFactory.createThreadPool(
                        configuration.getThreadPoolCoreSize(),
                        configuration.getThreadPoolMaxSize(),
                        configuration.getThreadPoolIdleTimeoutSeconds(),
                        TimeUnit.SECONDS,
                        getThreadPoolQueue(configuration.getThreadPoolQueueSize())
                ),
                new StandardScriptEvaluator(scriptEngineManager));
    }

    private BlockingQueue<Runnable> getThreadPoolQueue(int size) {
        return size == ScriptEngineConfiguration.UNBOUNDED_QUEUE_SIZE
                ? new ResizableLinkedBlockingQueue<Runnable>()
                : new ResizableLinkedBlockingQueue<Runnable>(size);
    }

}
