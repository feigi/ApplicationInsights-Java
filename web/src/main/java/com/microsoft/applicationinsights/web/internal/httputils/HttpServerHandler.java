package com.microsoft.applicationinsights.web.internal.httputils;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.common.CommonUtils;
import com.microsoft.applicationinsights.internal.logger.InternalLogger;
import com.microsoft.applicationinsights.internal.util.ThreadLocalCleaner;
import com.microsoft.applicationinsights.telemetry.Duration;
import com.microsoft.applicationinsights.telemetry.RequestTelemetry;
import com.microsoft.applicationinsights.web.internal.RequestTelemetryContext;
import com.microsoft.applicationinsights.web.internal.ThreadContext;
import com.microsoft.applicationinsights.web.internal.WebModulesContainer;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.List;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * This Helper Handler class provides the required methods to instrument requests.
 */
public final class HttpServerHandler {

    /**
     * Extractor to extract data from request and response
     */
    private final HttpExtractor extractor;

    /**
     * Container that holds collection of
     * {@link com.microsoft.applicationinsights.web.extensibility.modules.WebTelemetryModule}
     */
    private final WebModulesContainer webModulesContainer;

    /**
     * An instance of {@link TelemetryClient} responsible to track exceptions
     */
    private final TelemetryClient telemetryClient;

    /**
     * ThreadLocal Cleaners for Agent connector
     */
    private final List<ThreadLocalCleaner> cleaners;

    /**
     * Creates a new instance of {@link HttpServerHandler}
     *
     * @param extractor The {@code HttpExtractor} used to extract information from request and repsonse
     * @param webModulesContainer The {@code WebModulesContainer} used to hold
     *        {@link com.microsoft.applicationinsights.web.extensibility.modules.WebTelemetryModule}
     * @param telemetryClient The {@code TelemetryClient} used to send telemetry
     */
    public HttpServerHandler(HttpExtractor extractor,
        WebModulesContainer webModulesContainer,
        List<ThreadLocalCleaner> cleaners,
        /* Nullable */ TelemetryClient telemetryClient) {
        Validate.notNull(extractor, "extractor");
        Validate.notNull(webModulesContainer, "WebModuleContainer");
        Validate.notNull(cleaners, "ThreadLocalCleaners");
        this.extractor = extractor;
        this.webModulesContainer = webModulesContainer;
        this.cleaners = cleaners;
        this.telemetryClient = telemetryClient;
    }

    /**
     * This method is used to instrument incoming request and initiate correlation with help of
     * {@link com.microsoft.applicationinsights.web.extensibility.modules.WebRequestTrackingTelemetryModule#onBeginRequest(ServletRequest, ServletResponse)}
     * @param request incoming Request
     * @param response Response object
     * @return {@link RequestTelemetryContext} that contains correlation information and metadata about request
     * @throws MalformedURLException
     */
    public RequestTelemetryContext handleStart(ServletRequest request, ServletResponse response) throws MalformedURLException {
        RequestTelemetryContext context = new RequestTelemetryContext(new Date().getTime(),null);
        RequestTelemetry requestTelemetry = context.getHttpRequestTelemetry();
        ThreadContext.setRequestTelemetryContext(context);
        String method = extractor.getMethod(request);
        String userAgent = extractor.getUserAgent(request);
        String uriWithoutSessionId = extractor.getUri(request);
        String scheme = extractor.getScheme(request);
        String host = extractor.getHost(request);
        String query = extractor.getQuery(request);
        if (!CommonUtils.isNullOrEmpty(query)) {
            requestTelemetry.setUrl(scheme + "://" + host + uriWithoutSessionId + "?" + query);
        } else {
            requestTelemetry.setUrl(scheme + "://" + host + uriWithoutSessionId);
        }
        requestTelemetry.setHttpMethod(method);
        requestTelemetry.setName(method + " " + uriWithoutSessionId);
        requestTelemetry.getContext().getUser().setUserAgent(userAgent);
        requestTelemetry.setTimestamp(new Date(context.getRequestStartTimeTicks()));
        webModulesContainer.invokeOnBeginRequest(request, response);
        return context;
    }

    /**
     * This method is used to indicate request end instrumentation, complete correlation and record timing, response.
     * Context object is needed as a parameter because in Async requests, handleEnd() can be called
     * on separate thread then where handleStart() was called.
     * @param request HttpRequest object
     * @param response HttpResponse object
     * @param context RequestTelemetryContext object
     */
    public void handleEnd(ServletRequest request, ServletResponse response,
                          RequestTelemetryContext context) {
        RequestTelemetry requestTelemetry = context.getHttpRequestTelemetry();
        long endTime = new Date().getTime();
        requestTelemetry.setDuration(new Duration(endTime - context.getRequestStartTimeTicks()));
        int resultCode = extractor.getStatusCode(response);
        requestTelemetry.setSuccess(resultCode < 400);
        requestTelemetry.setResponseCode(Integer.toString(resultCode));
        if (ThreadContext.getRequestTelemetryContext() == null) {
            // e.g. when called from AIHttpServletListener
            ThreadContext.setRequestTelemetryContext(context);
            try {
                webModulesContainer.invokeOnEndRequest(request, response);
            } finally {
                ThreadContext.remove();
            }
        } else {
            webModulesContainer.invokeOnEndRequest(request, response);
        }
        cleanup();
    }

    /**
     * This method is used to capture runtime exceptions while processing request
     * @param e Exception occurred
     */
    public void handleException(Exception e) {
        try {
            InternalLogger.INSTANCE.trace("Unhandled exception while processing request: %s",
                ExceptionUtils.getStackTrace(e));
            if (telemetryClient != null) {
                telemetryClient.trackException(e);
            }
        } catch (Exception ex) {
            // swallow AI Exception
        }
    }

    /**
     * Remove data from Threadlocal and ThreadLocalCleaners
     */
    private void cleanup() {
        try {
            for (ThreadLocalCleaner cleaner : cleaners) {
                cleaner.clean();
            }
        } catch (Exception t) {
            InternalLogger.INSTANCE.warn(String.format("unable to perform TLS Cleaning: %s",
                ExceptionUtils.getStackTrace(t)));
        }
    }
}
