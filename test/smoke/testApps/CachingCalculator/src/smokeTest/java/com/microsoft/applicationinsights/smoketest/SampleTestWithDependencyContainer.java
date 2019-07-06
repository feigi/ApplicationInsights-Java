package com.microsoft.applicationinsights.smoketest;

import org.junit.*;

import static org.junit.Assert.*;

@UseAgent
@WithDependencyContainers(@DependencyContainer(value="redis", portMapping="6379"))
public class SampleTestWithDependencyContainer extends AiSmokeTest {

	// TODO FIXME this is a sample test for dependencies. it shouldn't count towards test coverage
	@Test
	@TargetUri("/index.jsp")
	public void doCalcSendsRequestDataAndMetricData() throws Exception {
		assertTrue("mocked ingestion has no data", mockedIngestion.hasData());
		assertTrue("mocked ingestion has 0 items", mockedIngestion.getItemCount() > 0);
		
		assertEquals(1, mockedIngestion.getCountForType("RequestData"));
		assertEquals(1, mockedIngestion.getCountForType("RemoteDependencyData"));
	}
}