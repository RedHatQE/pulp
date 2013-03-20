package com.redhat.qe.pulp.cli.tests;

import java.util.Hashtable;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import com.redhat.qe.auto.testng.TestNGUtils;
import com.redhat.qe.Assert;

import com.redhat.qe.pulp.cli.base.PulpTestScript;
import com.redhat.qe.pulp.abstraction.PulpAbstraction;
import com.redhat.qe.pulp.cli.tasks.PulpTasks;
import com.redhat.qe.tools.SSHCommandRunner;

public class SmokeTest extends PulpTestScript {
	String serverOutput;
	String clientOutput;
	public SmokeTest() {
		super();

		Hashtable<String, String> rules = new Hashtable<String, String>();
		rules.put("repoStatusName", "^Repository:[\\s]+([a-zA-Z0-9\\_]+)[\\s]*$");
		rules.put("repoStatusPkgCount", "^Number of Packages:[\\s]+([0-9]+)[\\s]*$");
		rules.put("repoStatusLastSync", "^Last Sync:[\\s]+([a-zA-Z0-9\\_]+)[\\s]*$");
		rules.put("repoStatusSyncing", "^Currently syncing:[\\s]+([0-9\\s\\%]+done[a-zA-Z0-9\\_\\(\\)\\%\\s]+)[\\s]*$");
		pulpAbs.appendRegexCriterion(rules);
	}

	@BeforeClass(groups={"smokeTest"}, description="create test repo")
	public void createRepo() throws ParseException, IOException {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			servertasks.createTestRepo(repoData);
		}
	}

	@BeforeClass(groups={"smokeTest"}, description="sync the repo",
		dependsOnMethods = {"createRepo"})
	public void syncRepo() throws ParseException, IOException {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(0)).replace("--id=", "");
			servertasks.syncTestRepo(repoId, true);	
		}
	}

	@BeforeClass(groups={"smokeTest"}, description="bind the consumers to test repo",
		dependsOnMethods = {"syncRepo"})
	public void bindConsumer() throws ParseException, IOException {
		for (List<Object> consumerRepo : getConsumerRepoData()) {
			String repoId = (String)consumerRepo.get(0);
			String consumerId = (String)consumerRepo.get(1);
			PulpTasks task = (PulpTasks)consumerRepo.get(2);
			task.bindConsumer(consumerId, repoId, true);
		}
	}

	@BeforeClass(description="pulp-cli: list the repos, check if repo has been properly added.", 
		groups={"smokeTest"}, dependsOnMethods = {"syncRepo"})
	public void createRepoListTest() {
		ArrayList<Hashtable<String, ArrayList<String>>> filteredResult = this.match(servertasks.listRepo(true), "   ");
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(0)).replace("--id=", "");
			String repoName = ((String)repoData.get(1)).replace("--name=", "");
			String repoArch = ((String)repoData.get(2)).replace("--arch=", "");
			String repoLocation = ((String)repoData.get(3)).replace("--feed=", "");

			Hashtable<String, ArrayList<String>> result = this.findRepo(filteredResult, repoId);
			Assert.assertEquals(result.get("Id").get(0), repoId, "comparing repoId to " + repoId);
			Assert.assertEquals(result.get("Name").get(0), repoName, "comparing repoName to " + repoName);
			Assert.assertEquals(result.get("Architecture").get(0), repoArch, "comparing repoArch to " + repoArch);
			if (repoLocation.contains("http")) {
				Assert.assertEquals(result.get("Feed Type").get(0), "remote", "comparing repoType to remote");
			}
			else {
				Assert.assertEquals(result.get("Feed Type").get(0), "local", "comparing repoType to local");
			}
			Assert.assertEquals(result.get("Feed URL").get(0), repoLocation, "comparing repoLocation to " + repoLocation);
		}
	}

	@Test (groups={"smokeTest"}) 
	public void checkServerForCustomRepo() {
		serverOutput = servertasks.yumRepoList();
		// this loop (ass)umes that all consumers in base/PulpTestScript 
		// are bound to all the repos
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(0)).replace("--id=", "");
			Assert.assertTrue(serverOutput.contains(repoId),"Checking server for default test repo listing.");
		}
	}

	@Test (groups={"smokeTest"}) 
	public void checkClientForCustomRepo() {
		clientOutput = clienttasks.yumRepoList();
		// this loop (ass)umes that all consumers in base/PulpTestScript 
		// are bound to all the repos
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(0)).replace("--id=", "");
			Assert.assertTrue(clientOutput.contains(repoId),"Checking client for default test repo listing.");
		}
	}

	@Test (groups={"smokeTest"}, dependsOnMethods={"checkServerForCustomRepo"}) 
	public void checkServerPackageCount() {
		// check output against pulp-admin repo info
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(0)).replace("--id=", "");
			int pkg_count = getYumPkgCount(serverOutput, repoId);
			int reportedPkgCount = pulpAbs.getRepoPkgCountFromStatus(this.match(servertasks.listRepoStatus(repoId), ":"), repoId, "Repository");
			Assert.assertEquals(reportedPkgCount, pkg_count, "Verifying package count reported by yum and pulp is identical.");
		}
	}

	@Test (groups={"smokeTest"}, dependsOnMethods={"checkClientForCustomRepo"}) 
	public void checkClientPackageCount() {
		// check output against pulp-admin repo info
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(0)).replace("--id=", "");
			int pkg_count = getYumPkgCount(clientOutput, repoId);
			int reportedPkgCount = pulpAbs.getRepoPkgCountFromStatus(this.match(servertasks.listRepoStatus(repoId), ":"), repoId, "Repository");
			Assert.assertEquals(reportedPkgCount, pkg_count, "Verifying package count reported by yum and pulp is identical.");
		}
	}

	// Negative Tests
	@Test (groups={"smokeTest"}) 
	public void mongodOffTest() {
		server.runCommandAndWait("service mongod stop");
		String rtn = servertasks.listRepo(false);
		Assert.assertTrue(rtn.contains("Connection refused") || rtn.contains("AutoReconnect"), "Making sure there's an connection issue being raised.");
		// reset
		server.runCommandAndWait("service mongod start");
	}

/*
	@Test (groups={"smokeTest"}) 
	public void httpdOffTest() {
		server.runCommandAndWait("service httpd stop");
		String rtn = servertasks.listRepo(false);
		Assert.assertTrue(rtn.contains("Traceback"), "Making sure there's some form of traceback.");
		Assert.assertTrue(rtn.contains("Connection refused"), "Making sure there's a Connection Refused issue");
		// reset
		//server.runCommandAndWait("service httpd start");
		server.runCommandAndWait("service mongod restart && service goferd restart && service qpidd restart && service httpd restart");
		try {
			Thread.currentThread().sleep(5000);
		} catch (Exception e) {}
	}
*/

	@AfterClass(groups={"smokeTest"}, alwaysRun=true)
	public void unbindConsumer() throws ParseException, IOException {
		for (List<Object> consumerRepo : getConsumerRepoData()) {
			String repoId = ((String)consumerRepo.get(0)).replace("--id=", "");
			String consumerId = (String)consumerRepo.get(1);
			PulpTasks task = (PulpTasks)consumerRepo.get(2);
			task.unbindConsumer(consumerId, repoId);
		}
	}

	@AfterClass(groups={"smokeTest"}, alwaysRun=true, dependsOnMethods = {"unbindConsumer"})
	public void deleteRepo() throws ParseException, IOException {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(0)).replace("--id=", "");
			servertasks.deleteTestRepo(repoId);	
		}
	}
	
	@AfterClass(description="pulp-cli: list the repos, check if repo has been properly deleted.", groups={"smokeTest"},
		alwaysRun=true, dependsOnMethods = {"deleteRepo"}) 
	public void deleteRepoListTest() {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = (String)repoData.get(0);
			ArrayList<Hashtable<String, ArrayList<String>>> filteredResult = this.match(servertasks.listRepo(true), ":");
			Hashtable<String, ArrayList<String>> result = pulpAbs.findRepo(filteredResult, repoId);
			Assert.assertNull(result.get("Id"), "comparing repoId to null.");
			Assert.assertNull(result.get("Name"), "comparing repoName to null.");
			Assert.assertNull(result.get("Arch"), "comparing repoArch to null.");
			Assert.assertNull(result.get("Type"), "comparing repoType to null.");
			Assert.assertNull(result.get("Feed URL"), "comparing repoLocation to null.");
		}
	}

	@AfterClass (groups={"smokeTest"}, alwaysRun=true, dependsOnMethods={"deleteRepoListTest"})
	public void cleanup() {
		// yum clean all
		servertasks.cleanCache();
		clienttasks.cleanCache();
	}

	@AfterClass (groups={"smokeTest"}, alwaysRun=true, dependsOnMethods={"cleanup"})
	public void restartServices() {
		// bounce all services so we don't leave the system in an unusable state
		server.runCommandAndWait("service mongod restart && service goferd restart && service qpidd restart && service httpd restart");
	}

	// ====================================================================
	@DataProvider(name="localRepoData")
	public Object[][] localRepoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalRepoData());
	}
	public List<List<Object>> getLocalRepoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();

		ArrayList<String> repoOpts = new ArrayList<String>();
		repoOpts.add("--id=smoketest_repo");
		repoOpts.add("--name=smoketest_repo");
		repoOpts.add("--arch=x86_64");
		//repoOpts.add("--feed=http://repos.fedorapeople.org/repos/pulp/pulp/dev/testing/fedora-17/x86_64/");
		repoOpts.add("--feed=" + System.getProperty("automation.resources.location") + "/updates");
		//repoOpts.add("--schedule=\""+repoSchedule+"\"");
		data.add(Arrays.asList(new Object[]{repoOpts}));
		return data;
	}

	@DataProvider(name="consumerRepoData")
	public Object[][] consumerRepoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getConsumerRepoData());
	}
	public List<List<Object>> getConsumerRepoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		// (¬_¬) this is rather crude... manual O(n^2) implementation
		//       curse you java! where's my zip()
		for (List<Object> consumer : getConsumerData()) {
			for (List<Object> repo : getLocalRepoData()) {
				ArrayList repoData = (ArrayList)repo.get(0);
				String repoId = ((String)repoData.get(0)).replace("--id=", "");
				data.add(Arrays.asList(new Object[]{repoId, (String)consumer.get(0), (PulpTasks)consumer.get(1)}));
			}
		}
		return data;
	}
}
