package com.redhat.qe.pulp.v2_cli.tests;

import com.redhat.qe.pulp.v2_cli.base.PulpTestScript;
import com.redhat.qe.pulp.v2_cli.tasks.PulpTasks;
import com.redhat.qe.auto.testng.TestNGUtils;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;

import com.redhat.qe.Assert;

public class ConsumerTest extends PulpTestScript {
	public ConsumerTest() {
		super();
	}

	@BeforeClass (groups={"testConsumer"})
	public void setupTestConsumer() {
		// First unregister the current consumer
		for (List<Object> consumer : getConsumerData()) {
			PulpTasks task = (PulpTasks)consumer.get(1);
			task.deleteConsumer((String)consumer.get(0));
		}
		// Now register local consumers
		for (List<Object> consumer : getLocalConsumerData()) {
			PulpTasks task = (PulpTasks)consumer.get(1);
			task.createConsumer((String)consumer.get(0), true);
		}
	}

	@Test (groups={"testConsumer"}, dataProvider="localConsumerData")
	public void duplicateConsumerCreate(String consumerId, PulpTasks task) {
		String stdout = task.createConsumer(consumerId, false);
		Assert.assertFalse(stdout.contains("Traceback"), "Making sure stdout does not contain any traceback.");
	}

	@Test (groups={"testConsumer"}, dataProvider="localConsumerData")
	public void bindNonexistRepo(String consumerId, PulpTasks task) {
		String stdout = task.bindConsumer(consumerId, "f14test_asdf", false);
		Assert.assertFalse(stdout.contains("Traceback"), "Making sure stdout does not contain any traceback.");
	}

	@Test (groups={"testConsumer"}, dataProvider="localConsumerData")
	public void multClientConsumer(String consumerId, PulpTasks task) {
		String consumer_postfix = "_multTest";
		String mult_consumerId = consumerId + consumer_postfix;
		String stdout = task.createConsumer(mult_consumerId, false); // this should snuff out the old consumer's cert
		Assert.assertFalse(stdout.contains("Traceback"), "Making sure stdout does not contain any traceback.");
	}

	@AfterClass (groups={"testConsumer"}, alwaysRun=true)
	public void removeTestConsumer() {
		for (List<Object> consumer : getLocalConsumerData()) {
			PulpTasks task = (PulpTasks)consumer.get(1);
			task.deleteConsumer((String)consumer.get(0));
		}
		// Create global consumer back
		for (List<Object> consumer : getConsumerData()) {
			PulpTasks task = (PulpTasks)consumer.get(1);
			task.createConsumer((String)consumer.get(0), true);
		}
	}

	@DataProvider(name="localConsumerData")
	public Object[][] localConsumerData() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalConsumerData());
	}
	public List<List<Object>> getLocalConsumerData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		for (List<Object> consumer : getConsumerData()) {
			String consumerId = (String)consumer.get(0);
			data.add(Arrays.asList(new Object[]{consumerId.concat("_jkig"), (PulpTasks)consumer.get(1)}));
		}
		return data;
	}
}
