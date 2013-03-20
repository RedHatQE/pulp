package com.redhat.qe.pulp.cli.tests;

import com.redhat.qe.pulp.cli.base.PulpTestScript;
import com.redhat.qe.pulp.cli.tasks.PulpTasks;
import com.redhat.qe.auto.testng.TestNGUtils;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import com.redhat.qe.tools.SSHCommandRunner;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;

import com.redhat.qe.Assert;

public class CDSTest extends PulpTestScript {
	// This need to change to something that could contain multiple hostnames
	protected String cdsHostname			= System.getProperty("pulp.cds.hostname");
	private ArrayList<SSHCommandRunner> cdsNodes;
	private ArrayList<String> cdsHosts;

	public CDSTest() {
		super();

		cdsNodes = new ArrayList<SSHCommandRunner>();
		cdsHosts = new ArrayList<String>();

		try {
			for (String hostname : cdsHostname.split(":")) {
				cdsNodes.add(new SSHCommandRunner(hostname, sshUser, sshPassphrase, sshKeyPrivate, sshKeyPassphrase, null));
				cdsHosts.add(hostname);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	@BeforeClass (groups={"testCDS"})
	public void installCDSPackages() {
		// install cds packages on remote nodes...
		if (Boolean.parseBoolean(reinstall_flag)) {
			for (SSHCommandRunner node : cdsNodes) {
				node.runCommandAndWait("yum remove -y pulp* grinder* gofer* && yum clean all");
				node.runCommandAndWait("yum install -y --nogpg pulp-cds");
				node.runCommandAndWait("sed -i s/localhost/"+serverHostname+"/g /etc/gofer/agent.conf");
				node.runCommandAndWait("sed -i s/host\\ =\\ localhost\\.localdomain/host\\ =\\ "+serverHostname+"/g /etc/pulp/cds.conf");

				node.runCommandAndWait("service pulp-cds restart");
			}
		}
	}
	
/*
	@BeforeClass (groups={"testCDS"}, dependsOnMethods={"installCDSPackages"}) 
	public void createCDSConsumer() {
		//for (List<Object> consumer : getLocalConsumerData()) {
		for (List<Object> consumer : getConsumerData()) {
			PulpTasks task = (PulpTasks)consumer.get(1);
			task.createConsumer((String)consumer.get(0), true);
		}
	}
*/

	@BeforeClass (groups={"testCDS"}, dependsOnMethods={"installCDSPackages"})
	public void createCDSRepo() {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			//String repoId = ((String)repoData.get(0)).replace("--id=", "");
			servertasks.createTestRepo(repoData);
		}
	}
	
	@Test (groups={"testCDS"})
	public void registerCDS() {
		for (String hostname : cdsHosts) {
			servertasks.registerCDS(hostname);
		}
	}

	// ============== Scenario 1 ===============
	@Test (groups={"testCDS"}, dataProvider="localRepoData", dependsOnMethods={"registerCDS"})
	public void associateRepo(ArrayList repoOpts) {
		String repoId = ((String)repoOpts.get(0)).replace("--id=", "");
		for (String hostname : cdsHosts) {
			servertasks.associateRepoToCDS(hostname, repoId);
		}
	}

	@Test (groups={"testCDS"}, dataProvider="localRepoData", dependsOnMethods={"associateRepo"})
	public void bindTestRepo(ArrayList repoOpts) {
		String repoId = ((String)repoOpts.get(0)).replace("--id=", "");
		//for (List<Object> consumer : getLocalConsumerData()) {
		for (List<Object> consumer : getConsumerData()) {
			String consumerId = (String)consumer.get(0);
			PulpTasks task = (PulpTasks)consumer.get(1);
			task.bindConsumer(consumerId,repoId, true);
		}
	}

	@Test (groups={"testCDS"}, dataProvider="localRepoData", dependsOnMethods={"bindTestRepo"})
	public void syncTestRepo(ArrayList repoOpts) {
		String repoId = ((String)repoOpts.get(0)).replace("--id=", "");
		//for (List<Object> consumer : getLocalConsumerData()) {
		for (List<Object> consumer : getConsumerData()) {
			PulpTasks task = (PulpTasks)consumer.get(1);
			task.syncTestRepo(repoId, true);
		}
	}

	@Test (groups={"testCDS"}, dataProvider="localRepoData", dependsOnMethods={"syncTestRepo"})
	public void unbindTestRepo(ArrayList repoOpts) {
		String repoId = ((String)repoOpts.get(0)).replace("--id=", "");
		//for (List<Object> consumer : getLocalConsumerData()) {
		for (List<Object> consumer : getConsumerData()) {
			String consumerId = (String)consumer.get(0);
			PulpTasks task = (PulpTasks)consumer.get(1);
			task.unbindConsumer(consumerId,repoId);
		}
	}

	@Test (groups={"testCDS"}, dataProvider="localRepoData", dependsOnMethods={"unbindTestRepo"}, alwaysRun=true)
	public void unassociateRepo(ArrayList repoOpts) {
		String repoId = ((String)repoOpts.get(0)).replace("--id=", "");
		for (String hostname : cdsHosts) {
			servertasks.unassociateRepoToCDS(hostname, repoId);
		}
	}

	// ============== Scenario 2 ===============
	@Test (groups={"testCDS"}, dataProvider="localRepoData", dependsOnMethods={"unassociateRepo"})
	public void bindSyncedRepo(ArrayList repoOpts) {
		// sync first
		syncTestRepo(repoOpts);

		// now bind
		String repoId = ((String)repoOpts.get(0)).replace("--id=", "");
		//for (List<Object> consumer : getLocalConsumerData()) {
		for (List<Object> consumer : getConsumerData()) {
			String consumerId = (String)consumer.get(0);
			PulpTasks task = (PulpTasks)consumer.get(1);
			task.bindConsumer(consumerId,repoId, true);
		}
	}

	@Test (groups={"testCDS"}, dataProvider="localRepoData", dependsOnMethods={"bindSyncedRepo"})
	public void associateSyncedRepo(ArrayList repoOpts) {
		String repoId = ((String)repoOpts.get(0)).replace("--id=", "");
		for (String hostname : cdsHosts) {
			servertasks.associateRepoToCDS(hostname, repoId);
		}
	}

	@Test (groups={"testCDS"}, dataProvider="localRepoData", dependsOnMethods={"associateSyncedRepo"}, alwaysRun=true)
	public void unassociateSyncedRepo(ArrayList repoOpts) {
		String repoId = ((String)repoOpts.get(0)).replace("--id=", "");
		for (String hostname : cdsHosts) {
			servertasks.unassociateRepoToCDS(hostname, repoId);
		}
	}

	@Test (groups={"testCDS"}, dataProvider="localRepoData", dependsOnMethods={"unassociateSyncedRepo"})
	public void unbindSyncedRepo(ArrayList repoOpts) {
		String repoId = ((String)repoOpts.get(0)).replace("--id=", "");
		//for (List<Object> consumer : getLocalConsumerData()) {
		for (List<Object> consumer : getConsumerData()) {
			String consumerId = (String)consumer.get(0);
			PulpTasks task = (PulpTasks)consumer.get(1);
			task.unbindConsumer(consumerId,repoId);
		}
	}
	// =========================================

	@Test (groups={"testCDS"}, dependsOnMethods={"unassociateRepo", "unbindSyncedRepo"})
	public void getCDSHistory() {
		for (String hostname : cdsHosts) {
			servertasks.getCDSHistory(hostname, "", true);
		}
	}

	@Test (groups={"testCDS"}, dataProvider="localCDSHistoryOpt", dependsOnMethods={"getCDSHistory"})
	public void getCDSHistorySorted(String opt, boolean expectedSuccess) {
		for (String hostname : cdsHosts) {
			servertasks.getCDSHistory(hostname, opt, expectedSuccess);
			// now htf do i verify the result?
		}
	}

	@Test (groups={"testCDS"}, dependsOnMethods={"getCDSHistorySorted"}, alwaysRun=true)
	public void unregisterCDS() {
		for (String hostname : cdsHosts) {
			servertasks.unregisterCDS(hostname);
		}
	}

/*
	@AfterClass (groups={"testCDS"})
	public void deleteCDSConsumer() {
		for (List<Object> consumer : getLocalConsumerData()) {
			PulpTasks task = (PulpTasks)consumer.get(1);
			task.deleteConsumer((String)consumer.get(0));
		}
	}
*/

	@AfterClass (groups={"testCDS"})
	public void deleteCDSRepo() {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(0)).replace("--id=", "");
			servertasks.deleteTestRepo(repoId);
		}
	}

	// ###########################################################################
	@DataProvider(name="localRepoData") 
	public Object[][] localRepoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalRepoData());
	}
	public List<List<Object>> getLocalRepoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();

		ArrayList<String> repoOpts = new ArrayList<String>();
		repoOpts.add("--id=cds_test_repo");
		repoOpts.add("--feed=" + System.getProperty("automation.resources.location") + "/updates/");
		data.add(Arrays.asList(new Object[]{repoOpts}));

		return data;
	}

	@DataProvider(name="localConsumerData")
	public Object[][] localConsumerData() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalConsumerData());
	}
	public List<List<Object>> getLocalConsumerData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		for (List<Object> consumer : getConsumerData()) {
			String consumerId = (String)consumer.get(0);
			data.add(Arrays.asList(new Object[]{consumerId.concat("_cds"), (PulpTasks)consumer.get(1)}));
		}
		return data;
	}

	@DataProvider(name="localCDSHistoryOpt")
	public Object[][] localCDSHistoryOpt() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalCDSHistoryOpt());
	}
	public List<List<Object>> getLocalCDSHistoryOpt() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		data.add(Arrays.asList(new Object[]{"--sort ascending", true}));
		data.add(Arrays.asList(new Object[]{"--sort descending", true}));
		data.add(Arrays.asList(new Object[]{"--limit 10", true}));
		data.add(Arrays.asList(new Object[]{"--event_type registered", true}));
		data.add(Arrays.asList(new Object[]{"--event_type unregistered", true}));
		data.add(Arrays.asList(new Object[]{"--event_type sync_started", true}));
		data.add(Arrays.asList(new Object[]{"--event_type sync_finished", true}));
		data.add(Arrays.asList(new Object[]{"--event_type repo_associated", true}));
		data.add(Arrays.asList(new Object[]{"--event_type repo_unassociated", true}));
		data.add(Arrays.asList(new Object[]{"--start_date 2011-06-20", true}));
		data.add(Arrays.asList(new Object[]{"--end_date 2011-06-20", true}));
		
		// mix and match
		return data;
	}
}
