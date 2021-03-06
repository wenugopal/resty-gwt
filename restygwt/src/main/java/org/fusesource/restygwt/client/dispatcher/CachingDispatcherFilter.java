/**
 * Copyright (C) 2009-2010 the original author or authors.
 * See the notice.md file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fusesource.restygwt.client.dispatcher;

import java.util.logging.Logger;

import org.fusesource.restygwt.client.Dispatcher;
import org.fusesource.restygwt.client.Method;
import org.fusesource.restygwt.client.cache.CacheKey;
import org.fusesource.restygwt.client.cache.QueueableCacheStorage;
import org.fusesource.restygwt.client.cache.UrlCacheKey;
import org.fusesource.restygwt.client.callback.CallbackFactory;
import org.fusesource.restygwt.client.callback.FilterawareRequestCallback;

import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.Response;
import com.google.gwt.logging.client.LogConfiguration;

public class CachingDispatcherFilter implements DispatcherFilter {

    /**
     * one instance of {@link QueueableCacheStorage}
     */
    private QueueableCacheStorage cacheStorage;

    /**
     * where to get a callback from. gives us the ability to use
     * customized {@link FilterawareRequestCallback}
     */
    private CallbackFactory callbackFactory;

    /**
     * the one and only constructor
     * @param cacheStorage
     * @param cf
     */
    public CachingDispatcherFilter(final QueueableCacheStorage cacheStorage,
            final CallbackFactory cf) {
        this.cacheStorage = cacheStorage;
        this.callbackFactory = cf;
    }

    /**
     * main filter method for a dispatcherfilter.
     *
     * @return continue filtering or not
     */
    public boolean filter(final Method method, final RequestBuilder builder) {
        final CacheKey cacheKey = new UrlCacheKey(builder);
        final Response cachedResponse = cacheStorage.getResultOrReturnNull(cacheKey);
        final boolean cachable = builder.getHTTPMethod().equals(RequestBuilder.GET.toString());

        if (cachable == true) {
            if (cachedResponse != null) {
                //case 1: we got a result in cache => return it...
                if (LogConfiguration.loggingIsEnabled()) {
                    Logger.getLogger(Dispatcher.class.getName())
                            .info("already got a cached response for: " + builder.getHTTPMethod() + " "
                            + builder.getUrl());
                }
                builder.getCallback().onResponseReceived(null, cachedResponse);
                return false;
            }  else {
                final RequestCallback retryingCallback = callbackFactory.createCallback(method);

                //case 2: => no cache in result => queue it....
                if (!cacheStorage.hasCallback(cacheKey)) {
                    //case 2.1 => first callback => make a new one and execute...
                    cacheStorage.addCallback(cacheKey, builder.getCallback());

                    if (LogConfiguration.loggingIsEnabled()) {
                        Logger.getLogger(Dispatcher.class.getName())
                                .info("Sending *caching* http request: " + builder.getHTTPMethod() + " "
                                + builder.getUrl());
                    }

                    // important part:
                    builder.setCallback(retryingCallback);
                    return true;
                } else {
                    //case 2.2 => a callback already in progress => queue to get response when back
                    if (LogConfiguration.loggingIsEnabled()) {
                        Logger.getLogger(Dispatcher.class.getName())
                                .info("request in progress, queue callback: " + builder.getHTTPMethod() + " "
                                + builder.getUrl());
                    }
                    cacheStorage.addCallback(cacheKey, retryingCallback);
                    return false;
                }
            }
        } else {
            // non cachable case
            if (LogConfiguration.loggingIsEnabled()) {
                String content = builder.getRequestData();
                Logger.getLogger(Dispatcher.class.getName())
                        .info("Sending *non-caching* http request: " + builder.getHTTPMethod() + " "
                        + builder.getUrl() + " (Content: `" + content + "´)");
            }

//            /*
//             * add X-Request-Token to all non-caching calls (!= GET) if we have some
//             */
//            builder.setHeader("X-Testing", "Fickbude");

            builder.setCallback(callbackFactory.createCallback(method));
            return true;
        }
    }
}
