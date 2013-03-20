package com.redhat.qe.pulp.perf.base;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Hashtable;

import java.io.IOException;
import java.text.ParseException;

import com.redhat.qe.pulp.cli.base.PulpTestScript;
import com.redhat.qe.pulp.perf.tasks.PerfPulpTasks;

import org.testng.annotations.DataProvider;
import com.redhat.qe.auto.testng.TestNGUtils;
import org.testng.annotations.BeforeSuite;

public class PerfPulpTestScript extends PulpTestScript {
	public PerfPulpTestScript() {
		super();

		try {
			clienttasks = new PerfPulpTasks(client);
			servertasks = new PerfPulpTasks(server);
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@BeforeSuite(groups="setup", description="setup for perf test on pulp box", dependsOnMethods={"setup"})
	public void perfSetup() throws ParseException, IOException {
		server.runCommandAndWait("echo '[tasking]\nmax_concurrent: 16' >> /etc/pulp/pulp.conf");
		server.runCommandAndWait("/etc/init.d/pulp-server init && /etc/init.d/pulp-server restart");
	}

	// Override
	@DataProvider(name="consumerData")
	public Object[][] consumerData() {
		return TestNGUtils.convertListOfListsTo2dArray(getConsumerData());
	}
	public List<List<Object>> getConsumerData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		data.add(Arrays.asList(new Object[]{consumerId, servertasks}));
		return data;
	}
}
