package com.microsoft.applicationinsights.testapps.perf.boot.controllers;

import com.google.common.io.CharStreams;
import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.telemetry.Duration;
import com.microsoft.applicationinsights.testapps.perf.TestCaseRunnable;
import com.microsoft.applicationinsights.testapps.perf.boot.SpringBootPerfTestHelper;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;

@RestController
public class FakeRestController {

    @Autowired
    private TelemetryClient tc;

    @GetMapping("/fakeRest")
    public String fakeRest(@RequestParam(value = "url", required = false) final String pUrl) {
        return SpringBootPerfTestHelper.runTest(new TestCaseRunnable(new Runnable() {
            @Override
            public void run() {
                final String url = (pUrl == null)
                        ? "http://www.msn.com"
                        : ((pUrl.startsWith("http://"))
                        ? pUrl
                        : "http://"+pUrl);

                try (CloseableHttpClient client = HttpClientBuilder.create().disableAutomaticRetries().build()) {
                    HttpGet get = new HttpGet(url);
                    try (CloseableHttpResponse getResp = client.execute(get)) {
                        HttpEntity entity = getResp.getEntity();
                        StringWriter cw = new StringWriter();
                        CharStreams.copy(new InputStreamReader(entity.getContent()), cw);
                        EntityUtils.consume(entity);
                        StatusLine status = getResp.getStatusLine();
                        System.out.printf("GET %s responded %d %s. response size: %d%n", url, status.getStatusCode(), status.getReasonPhrase(), cw.toString().length());
                    }
                    catch (IOException e) {
                        System.err.println("Error requesting "+url+".");
                        e.printStackTrace();
                    }
                }
                catch (IOException e) {
                    System.err.println("Error creating http client");
                    e.printStackTrace();
                }
                tc.trackDependency("FakeRestDependency", "fakeRestCommand", new Duration(123L), true);
                tc.trackEvent("FakeRestEvent");
                tc.trackMetric("FakeRestMetric", 1.0);
                tc.trackTrace("FakeRestTrace");
            }
        }, "fakeRest operation"));
    }
}
