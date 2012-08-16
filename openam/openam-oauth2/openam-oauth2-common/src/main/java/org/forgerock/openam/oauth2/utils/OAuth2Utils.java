/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 ForgeRock Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */

package org.forgerock.openam.oauth2.utils;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;

import org.forgerock.openam.oauth2.OAuth2;
import org.forgerock.openam.oauth2.provider.ClientVerifier;
import org.forgerock.openam.oauth2.provider.OAuth2TokenStore;
import org.forgerock.openam.oauth2.exceptions.OAuthProblemException;
import org.restlet.Context;
import org.restlet.Request;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Reference;
import org.restlet.engine.util.ChildContext;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.representation.EmptyRepresentation;
import org.restlet.resource.ResourceException;
import org.restlet.routing.Redirector;

/**
 * Utilities related to OAuth2.
 */
public class OAuth2Utils {

    public static enum ParameterLocation {
        HTTP_QUERY, HTTP_HEADER, HTTP_FRAGMENT, HTTP_BODY;

        @SuppressWarnings(value = "unchecked")
        public Map<String, String> getParameters(Request request) {
            Map<String, String> result = null;
            switch (this) {
            case HTTP_FRAGMENT:
                return new Form(request.getReferrerRef().getFragment()).getValuesMap();
            case HTTP_HEADER:
                if (null != request.getChallengeResponse()
                        && !request.getChallengeResponse().getParameters().isEmpty()) {
                    return new Form(request.getChallengeResponse().getParameters()).getValuesMap();
                }
                return null;
            case HTTP_QUERY:
                // Merge the parameterd from query and body
                result = request.getResourceRef().getQueryAsForm().getValuesMap();
            case HTTP_BODY:
                if (null == result) {
                    result = new LinkedHashMap<String, String>();
                }
                if (null != request.getEntity()) {
                    if (MediaType.APPLICATION_WWW_FORM.equals(request.getEntity().getMediaType())) {
                        Form form = new Form(request.getEntity());
                        // restore the entity body
                        request.setEntity(form.getWebRepresentation());
                        result.putAll(form.getValuesMap());
                    } else if (MediaType.APPLICATION_JSON
                            .equals(request.getEntity().getMediaType())) {
                        JacksonRepresentation<Map> representation =
                                new JacksonRepresentation<Map>(request.getEntity(), Map.class);
                        try {
                            result.putAll(representation.getObject());
                        } catch (IOException e) {
                            throw new ResourceException(e);
                        }
                        request.setEntity(representation);
                    }
                }
                return result;
            default:
                return null;
            }
        }

        /**
         * TODO How to use targetPattern?? TODO Use Custom Redirector to encode
         * variables in protected Reference getTargetRef(Request request,
         * Response response)
         * 
         * @param context
         * @return
         */
        public Redirector getRedirector(Context context, OAuthProblemException exception) {
            /*
             * 3.1.2.4. Invalid Endpoint
             * 
             * If an authorization request fails validation due to a missing,
             * invalid, or mismatching redirection URI, the authorization server
             * SHOULD inform the resource owner of the error, and MUST NOT
             * automatically redirect the user-agent to the invalid redirection
             * URI.
             */
            if (null != exception.getRedirectUri()) {
                // TODO handle non URL urn:ietf:wg:oauth:2.0:oob
                Reference cb = new Reference(exception.getRedirectUri());
                switch (this) {
                case HTTP_FRAGMENT: {
                    // Redirect URI can not contain Fragment so we can set it
                    cb.setFragment(exception.getErrorForm().getQueryString());
                    break;
                }
                case HTTP_QUERY: {
                    cb.addQueryParameters(exception.getErrorForm());
                    break;
                }
                default:
                    return null;
                }
                return new Redirector(context, cb.toString(), Redirector.MODE_CLIENT_FOUND);
            }
            return null;
        }
    }

    public static final String ACCESS_TOKEN_PATH = "access_token_path";
    public static final String AUTHORIZE_PATH = "authorize_path";
    public static final String TOKENINFO_PATH = "tokeninfo_path";
    public static final String ACCESS_TOKEN = "access_token";
    public static final String AUTHORIZE = "authorize";
    public static final String TOKENINFO = "tokeninfo";
    public static final String SCOPE_DELIMITER_CONFIG = "scope_delimiter";
    public static final String SCOPE_DELIMITER = " ";

    /**
     * Returns the value of the "access_token_path" parameter.
     * 
     * @param context
     *            The context where to find the parameter.
     * @return The value of the "access_token_path" parameter.
     */
    public static String getAccessTokenPath(Context context) {
        String path = null;
        Context parent = context;
        do {
            path = parent.getParameters().getFirstValue(ACCESS_TOKEN_PATH);
            if (null == path && parent instanceof ChildContext) {
                try {
                    java.lang.reflect.Method getParentContext =
                            ChildContext.class.getDeclaredMethod("getParentContext");
                    getParentContext.setAccessible(true);
                    parent = (Context) getParentContext.invoke(parent, null);
                } catch (Exception e) {
                    parent = null;
                }
            } else {
                parent = null;
            }
        } while (null == path && null != parent);
        path = null != path ? path : ACCESS_TOKEN;
        return path.startsWith("/") ? path : "/" + path;
    }

    /**
     * Returns the value of the "access_token_path" parameter.
     * 
     * @param context
     *            The context where to find the parameter.
     * @return The value of the "access_token_path" parameter.
     */
    public static String getAuthorizePath(Context context) {
        String path = null;
        Context parent = context;
        do {
            path = parent.getParameters().getFirstValue(AUTHORIZE_PATH);
            if (null == path && parent instanceof ChildContext) {
                try {
                    java.lang.reflect.Method getParentContext =
                            ChildContext.class.getDeclaredMethod("getParentContext");
                    getParentContext.setAccessible(true);
                    parent = (Context) getParentContext.invoke(parent, null);
                } catch (Exception e) {
                    parent = null;
                }
            } else {
                parent = null;
            }
        } while (null == path && null != parent);
        path = null != path ? path : AUTHORIZE;
        return path.startsWith("/") ? path : "/" + path;
    }

    /**
     * Returns the value of the "tokeninfo_path" parameter.
     * 
     * @param context
     *            The context where to find the parameter.
     * @return The value of the "tokeninfo_path" parameter.
     */
    public static String getTokenInfoPath(Context context) {
        String path = context.getParameters().getFirstValue(TOKENINFO_PATH, TOKENINFO);
        return path.startsWith("/") ? path : "/" + path;
    }

    /**
     * Returns the value of the "scope_delimiter" parameter.
     * 
     * @param context
     *            The context where to find the parameter.
     * @return The value of the "scope_delimiter" parameter.
     */
    public static String getScopeDelimiter(Context context) {
        if (null != context) {
            return context.getParameters().getFirstValue(SCOPE_DELIMITER_CONFIG, false,
                    SCOPE_DELIMITER);
        } else {
            return SCOPE_DELIMITER;
        }
    }

    /**
     * Returns the value of the "ClientVerifier" parameter.
     * 
     * @param context
     *            The context where to find the parameter.
     * @return The value of the "ClientVerifier" parameter.
     */
    public static ClientVerifier getClientVerifier(Context context) {
        Object o = context.getAttributes().get(ClientVerifier.class.getName());
        if (o instanceof ClientVerifier) {
            return (ClientVerifier) o;
        }
        return null;
    }

    /**
     * Returns the value of the "OAuth2TokenStore" parameter.
     * 
     * @param context
     *            The context where to find the parameter.
     * @return The value of the "OAuth2TokenStore" parameter.
     */
    public static OAuth2TokenStore getTokenStore(Context context) {
        Object o = context.getAttributes().get(OAuth2TokenStore.class.getName());
        if (o instanceof OAuth2TokenStore) {
            return (OAuth2TokenStore) o;
        }
        return null;
    }

    /**
     * Sets the value of the "access_token_path" parameter.
     * 
     * @param value
     *            The value of the "access_token_path" parameter
     * @param context
     *            The context where to set the parameter.
     */
    public static void setAccessTokenPath(String value, Context context) {
        context.getParameters().set(ACCESS_TOKEN_PATH, value);
    }

    /**
     * Sets the value of the "authorize_path" parameter.
     * 
     * @param value
     *            The value of the "authorize_path" parameter
     * @param context
     *            The context where to set the parameter.
     */
    public static void setAuthorizePath(String value, Context context) {
        context.getParameters().set(AUTHORIZE_PATH, value);
    }

    /**
     * Sets the value of the "tokeninfo_path" parameter.
     * 
     * @param value
     *            The value of the "tokeninfo_path" parameter
     * @param context
     *            The context where to set the parameter.
     */
    public static void setTokenInfoPath(String value, Context context) {
        context.getParameters().set(TOKENINFO_PATH, value);
    }

    /**
     * Sets the value of the "scope_delimiter" parameter.
     * 
     * @param value
     *            The value of the "scope_delimiter" parameter
     * @param context
     *            The context where to set the parameter.
     */
    public static void setScopeDelimiter(String value, Context context) {
        context.getParameters().set(SCOPE_DELIMITER_CONFIG, value);
    }

    /**
     * Sets the value of the "realm" parameter.
     * 
     * @param value
     *            The value of the "realm" parameter
     * @param context
     *            The context where to set the parameter.
     */
    public static void setContextRealm(String value, Context context) {
        context.getParameters().set(OAuth2.Custom.REALM, value);
    }

    /**
     * Sets the value of the "scope_delimiter" parameter.
     * 
     * @param value
     *            The value of the "scope_delimiter" parameter
     * @param context
     *            The context where to set the parameter.
     */
    public static void setTokenStore(OAuth2TokenStore value, Context context) {
        context.getAttributes().put(OAuth2TokenStore.class.getName(), value);
    }

    /**
     * Sets the value of the "scope_delimiter" parameter.
     * 
     * @param value
     *            The value of the "scope_delimiter" parameter
     * @param context
     *            The context where to set the parameter.
     */
    public static void setClientVerifier(ClientVerifier value, Context context) {
        context.getAttributes().put(ClientVerifier.class.getName(), value);
    }

    /**
     * Get the realm from the Attributes first and then look for the realm in
     * the request.
     * <p/>
     * Example: Restlet Template populates the realm into the
     * {@link Request#attributes} {@code TemplateRoute route =
     * router.attach("/oauth2/ realm}/authorize", (Restlet)authorization);}
     * <p/>
     * Example: Custom code fetches it from the query, the body or more secure
     * from the User Session
     * 
     * @param request
     * @return
     */
    public static String getRealm(Request request) {
        Object realm = request.getAttributes().get(OAuth2.Custom.REALM);
        if (realm instanceof String) {
            return (String) realm;
        }
        return getRequestParameter(request, OAuth2.Custom.REALM, String.class);
    }

    public static String getModuleName(Request request) {
        Object module = request.getAttributes().get(OAuth2.Custom.MODULE);
        if (module instanceof String) {
            return (String) module;
        }
        return getRequestParameter(request, OAuth2.Custom.MODULE, String.class);
    }

    public static String getServiceName(Request request) {
        Object service = request.getAttributes().get(OAuth2.Custom.SERVICE);
        if (service instanceof String) {
            return (String) service;
        }
        return getRequestParameter(request, OAuth2.Custom.SERVICE, String.class);
    }

    public static String getLocale(Request request) {
        Object locale = request.getAttributes().get(OAuth2.Custom.LOCALE);
        if (locale instanceof String) {
            return (String) locale;
        }
        return getRequestParameter(request, OAuth2.Custom.LOCALE, String.class);
    }

    public static <T> T getRequestParameter(Request request, String parameterName, Class<T> clazz) {
        Object value = getRequestParameters(request).get(parameterName);
        if (null != value && clazz.isAssignableFrom(value.getClass())) {
            return clazz.cast(value);
        }
        return null;
    }

    /**
     * It copies the given parameters only once!!!
     * way the CallResolver can use it and the FreeMarker can list and add all
     * into the generated form
     * 
     * @param request
     *            incoming request object
     * @return The modifiable attributes map.
     */
    public static Map<String, Object> getRequestParameters(Request request) {
        Map<String, String> parameters = null;
        if (request.getAttributes().get(OAuth2.Params.class.getName()) instanceof Map == false) {
            parameters = getParameters(request);
            if (null != parameters) {
                // Copy the parameter for CallResolver
                request.getAttributes().putAll(parameters);
            }
            // Avoid reprocess the request next time.
            request.getAttributes().put(OAuth2.Params.class.getName(), parameters);
        }
        return request.getAttributes();
    }

    /**
     * Get the parameters from the request.
     * <p/>
     * If the method is GET then the parameters are fetched from the query If
     * the request has no body/payload then the parameters are fetched from the
     * query If the content type is "application/x-www-form-urlencoded" then the
     * parameters are fetched from the body
     * 
     * @param request
     *            incoming request object
     * @return null if the request does not contains any parameter
     */
    public static Map<String, String> getParameters(Request request) {
        if (Method.GET.equals(request.getMethod())
                || request.getEntity() instanceof EmptyRepresentation) {
            return OAuth2Utils.ParameterLocation.HTTP_QUERY.getParameters(request);
        } else {
            return OAuth2Utils.ParameterLocation.HTTP_QUERY.getParameters(request);
        }
    }

    /**
     * Determines if a string is empty. Empty is defined as null or empty
     * string.
     * <p/>
     * 
     * <pre>
     *  OAuth2Utils.isEmpty(null)               = true
     *  OAuth2Utils.isEmpty(&quot;&quot;)       = true
     *  OAuth2Utils.isEmpty(&quot; &quot;)      = false
     *  OAuth2Utils.isEmpty(&quot;bob&quot;)    = false
     *  OAuth2Utils.isEmpty(&quot; bob &quot;)  = false
     * </pre>
     * 
     * @param val
     *            string to evaluate as empty.
     * @return true if the string is empty else false.
     */
    public static boolean isEmpty(String val) {
        return (val == null) ? true : "".equals(val) ? true : false;
    }

    /**
     * Determines if a string is not empty. Its the exact opposite for
     * {@link #isEmpty(String)}.
     * 
     * @param val
     *            string to evaluate.
     * @return true if the string is not empty
     */
    public static boolean isNotEmpty(String val) {
        return !isEmpty(val);
    }

    /**
     * <pre>
     *      OAuth2Utils.isBlank(null)                = true
     *      OAuth2Utils.isBlank(&quot;&quot;)        = true
     *      OAuth2Utils.isBlank(&quot; &quot;)       = true
     *      OAuth2Utils.isBlank(&quot;bob&quot;)     = false
     *      OAuth2Utils.isBlank(&quot;  bob  &quot;) = false
     * </pre>
     */
    public static boolean isBlank(String val) {
        return (val == null) ? true : isEmpty(val.trim());
    }

    public static boolean isNotBlank(String val) {
        return !isBlank(val);
    }

    public static String join(Iterable<? extends Object> iterable, String delimiter) {
        if (null != iterable) {
            Iterator<? extends Object> iterator = iterable.iterator();
            if (!iterator.hasNext()) {
                return null;
            }
            StringBuilder buffer = new StringBuilder();
            buffer.append(iterator.next());
            String d = null != delimiter ? delimiter : SCOPE_DELIMITER;
            while (iterator.hasNext()) {
                buffer.append(d).append(iterator.next());
            }
            return buffer.toString();
        }
        return null;
    }

    public static Set<String> split(String string, String delimiter) {
        if (isNotBlank(string)) {
            StringTokenizer tokenizer =
                    new StringTokenizer(string, null != delimiter ? delimiter : SCOPE_DELIMITER);
            Set<String> result = new TreeSet<String>();
            while (tokenizer.hasMoreTokens()) {
                result.add(tokenizer.nextToken());
            }
            return Collections.unmodifiableSet(result);
        } else {
            return Collections.emptySet();
        }
    }

    /**
     * Constructor.
     */
    private OAuth2Utils() {
    }
}
