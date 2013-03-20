package com.redhat.qe.pulp.cli.tests;

import java.util.logging.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Hashtable;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;

import com.redhat.qe.pulp.cli.base.PulpTestScript;
import com.redhat.qe.pulp.cli.tasks.PulpTasks;
import com.redhat.qe.auto.testng.TestNGUtils;
import com.redhat.qe.Assert;

import com.redhat.qe.pulp.abstraction.PulpAbstraction;
import com.redhat.qe.tools.SSHCommandRunner;

/*
 * TODO: - Clones of clone?
 *       - Errata install off a cloned repo?
 *       - Clone of CDN repo
 *	 - Ideally, there should be some afterclass methods to unbind and remove pkgs, 
 *         but getting a string from an arraylist inside an arraylist is scary.
 */

public class RepoCloningTest extends PulpTestScript {
	protected static Logger log = Logger.getLogger(RepoCloningTest.class.getName());

	public RepoCloningTest() {
		super();
		Hashtable<String, String> rules = new Hashtable<String, String>();
		
		rules.put("repoStatusName", "^Repository:[\\s]+([a-zA-Z0-9\\_]+)[\\s]*$");
		rules.put("repoStatusPkgCount", "^Number of Packages:[\\s]+([0-9]+)[\\s]*$");
		rules.put("repoStatusLastSync", "^Last Sync:[\\s]+([a-zA-Z0-9\\_]+)[\\s]*$");
		rules.put("repoStatusSyncing", "^Currently syncing:[\\s]+([0-9\\s\\%]+done[a-zA-Z0-9\\_\\(\\)\\%\\s]+)[\\s]*$");

		pulpAbs.appendRegexCriterion(rules);

	}

	@BeforeClass (groups={"testRepoCloning"})
	public void createFeedRepo() {
		ArrayList<String> newRepoData = new ArrayList<String>();
		newRepoData.add("--id=feed_repo");
		newRepoData.add("--feed=" + System.getProperty("automation.resources.location") + "/updates/");
		servertasks.createTestRepo(newRepoData);

		servertasks.syncTestRepo("feed_repo", true);
	}

	@BeforeClass (groups={"testRepoCloning"})
	public void createFeedlessRepo() {
		ArrayList<String> newRepoData = new ArrayList<String>();
		newRepoData.add("--id=feedless_repo");
		servertasks.createTestRepo(newRepoData);
	}

	@Test (groups={"testRepoCloning"}, dataProvider="localRepoData")
	public void createClones(ArrayList<String> repoOpts, String cloneType) {
		servertasks.cloneTestRepo(repoOpts);
	}

	@Test (groups={"testRepoCloning"}, dataProvider="localRepoData", dependsOnMethods={"createClones"})
	public void verifyCloneCreation(ArrayList<String> repoOpts, String cloneType) {
		String clonedRepoId = repoOpts.get(1).replace("--clone_id=", "");
		ArrayList<Hashtable<String, ArrayList<String>>> filteredResult = this.match(servertasks.listRepo(true), "   ");
		Hashtable<String, ArrayList<String>> result = pulpAbs.findRepo(filteredResult, clonedRepoId);
		Assert.assertEquals(result.get("Id").get(0), clonedRepoId, "comparing repos id: "+ result.get("Id").get(0) +" to " + clonedRepoId);

		Hashtable<String, ArrayList<String>> parent_result = pulpAbs.findRepo(filteredResult, "feed_repo");

		if (cloneType.equals("parent")) {
			Assert.assertEquals(result.get("Feed Type").get(0), "local", "Comparing feed type");
			Assert.assertTrue(result.get("Feed URL").get(0).contains("var/lib/pulp"), "Clone url should contain local directory.");
		}
		else if (cloneType.equals("feedless")) {
			Assert.assertEquals(result.get("Feed Type").get(0), "None", "Comparing feed type");
			Assert.assertEquals(result.get("Feed URL").get(0), "None", "Clone url should contain no directory.");
		}
		else if (cloneType.equals("origin")) {
			Assert.assertEquals(result.get("Feed Type").get(0), "remote", "Comparing feed type");
			Assert.assertEquals(result.get("Feed URL").get(0), parent_result.get("Feed URL").get(0), "Comparing feedURL of clone against parent.");
		}
	}

	@Test (groups={"testRepoCloning"}, dataProvider="localRepoData", dependsOnMethods={"verifyCloneCreation"})
	public void bindClonedRepo(ArrayList<String> repoOpts, String cloneType) {
		String clonedRepoId = repoOpts.get(1).replace("--clone_id=", "");
		
		for (List<Object> consumer : getConsumerData()) {
			String consumerId = (String)consumer.get(0);
			PulpTasks task = (PulpTasks)consumer.get(1);

			task.bindConsumer(consumerId, clonedRepoId, true);
		}
	}

	@Test (groups={"testRepoCloning"}, dataProvider="localRepoData", dependsOnMethods={"bindClonedRepo"})
	public void repoYumCheck(ArrayList<String> repoOpts, String cloneType) {
		String clonedRepoId = repoOpts.get(1).replace("--clone_id=", "");
		int pkg_count = getYumPkgCount(servertasks.yumRepoList(), clonedRepoId);
		int reportedPkgCount = pulpAbs.getRepoPkgCountFromStatus(this.match(servertasks.listRepoStatus(clonedRepoId), ":"), clonedRepoId, "Repository");
		Assert.assertEquals(reportedPkgCount, pkg_count, "Verifying package count reported by yum and pulp is identical.");
	}

   	@Test (groups={"testRepoCloning"}, dependsOnMethods={"repoYumCheck"})
	public void adjustRootFeedlessRepo() {
		this.getMetadataStatus("feedless_repo");
		servertasks.uploadContent("feedless_repo", "feedless-1.0-1.noarch.rpm", System.getProperty("automation.resources.location") + "/updates/feedless-1.0-1.noarch.rpm", tmpRpmDir, true);
	}
 
   	@Test (groups={"testRepoCloning"}, dataProvider="localRepoData", dependsOnMethods={"adjustRootFeedlessRepo"})
	public void checkClonedRepo(ArrayList<String> repoOpts, String cloneType) {
		String clonedRepoId = repoOpts.get(1).replace("--clone_id=", "");
		String feed = repoOpts.get(2).replace("--feed=", "");

		servertasks.cleanCache();
		if (!(feed.equals("none"))) {
			servertasks.syncTestRepo(clonedRepoId, true);
		}

		int pkg_count = getYumPkgCount(servertasks.yumRepoList(), clonedRepoId);
		int reportedPkgCount = pulpAbs.getRepoPkgCountFromStatus(this.match(servertasks.listRepoStatus(clonedRepoId), ":"), clonedRepoId, "Repository");

		if (cloneType.equals("parent") || cloneType.equals("origin")) {
			// things should not have changed as only the feedless repo got mod
			int reportedPkgCountParent = pulpAbs.getRepoPkgCountFromStatus(this.match(servertasks.listRepoStatus("feed_repo"), ":"), "feed_repo", "Repository");
			Assert.assertTrue((reportedPkgCountParent == pkg_count && reportedPkgCountParent == reportedPkgCount), "Comparing unchanged pkg counts.");
		}
		else if (cloneType.equals("feedless")) {
			int reportedPkgCountParent = pulpAbs.getRepoPkgCountFromStatus(this.match(servertasks.listRepoStatus("feedless_repo"), ":"), "feedless_repo", "Repository");
			Assert.assertEquals(reportedPkgCountParent, 1, "Comparing feedless parent pkg counts.");
			Assert.assertTrue((reportedPkgCount == 0 && pkg_count == 0), "Comparing feedless clone pkg counts to 0 to make sure it did not change.");
		}
	}

   	@Test (groups={"testRepoCloning"}, dataProvider="localRepoData", dependsOnMethods={"checkClonedRepo"})
	public void prepInstallTestPkg(ArrayList<String> repoOpts, String cloneType) {
		String clonedRepoId = repoOpts.get(1).replace("--clone_id=", "");
		for (List<Object> consumer : getConsumerData()) {
			String consumerId = (String)consumer.get(0);
			PulpTasks task = (PulpTasks)consumer.get(1);

			task.unbindConsumer(consumerId, clonedRepoId);
		}
	}

   	@Test (groups={"testRepoCloning"}, dataProvider="localConsumerRepoData", dependsOnMethods={"prepInstallTestPkg"})
	public void installTestPkg(ArrayList<String> repoOpts, String cloneType, String consumerId, PulpTasks task) {

		// this is dangerous
		String clonedRepoId = repoOpts.get(1).replace("--clone_id=", "");
		String pkgName = cloneType; // pkg name is the same as clone type
		String feed = repoOpts.get(2).replace("--feed=", "");

		if (feed.equals("none")) {
			// for now, nothing since there's no pkg in this repo
			log.info("Skipping package installation since feedless repo has no pkg.");
		}
		else {
			try {
				task.bindConsumer(consumerId, clonedRepoId, true);

				uninstallPkg(pkgName, task); // just in case things didn't clean up correctly
				String result = task.installPackage(pkgName, consumerId);
				Assert.assertTrue(result.contains(consumerId+"  [ SUCCEEDED ]"), "Verifying package installation was successful.");
			} catch (Exception e) {
			} finally {
				// cleanup
				uninstallPkg(pkgName, task);
				task.unbindConsumer(consumerId, clonedRepoId);
			}
		}
	}

   	/* Do we need to go further? 
     * Setup/check errata install off a cloned repo as well?
     */

	@AfterClass (groups={"testRepoCloning"}, alwaysRun=true)
	public void removeClonedRepos() {
		String result;
		String repoId;
		for (Object[] obj : localRepoData()) {
			ArrayList repoData = (ArrayList)obj[0];
			repoId = ((String)repoData.get(1)).replace("--clone_id=", "");
			servertasks.deleteTestRepo(repoId);
		}

		repoId="feed_repo";
		servertasks.deleteTestRepo(repoId);

		repoId = "feedless_repo";
		servertasks.deleteTestRepo(repoId);
	}

	@AfterClass (groups={"testRepoCloning"}, alwaysRun=true, dependsOnMethods={"removeClonedRepos"})
	public void verifyDeleteTestRepo() {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = (String)repoData.get(0);
			ArrayList<Hashtable<String, ArrayList<String>>> filteredResult = this.match(servertasks.listRepo(true), "   ");
			Hashtable<String, ArrayList<String>> result = pulpAbs.findRepo(filteredResult, repoId);
			Assert.assertNull(result.get("Id"), "comparing repoId to null.");
		}
	}

	@DataProvider(name="localRepoData")
	public Object[][] localRepoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalRepoData());
	}
	public List<List<Object>> getLocalRepoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();

		ArrayList<String> parentOpts = new ArrayList<String>();
		ArrayList<String> originOpts = new ArrayList<String>();
		ArrayList<String> feedlessOpts = new ArrayList<String>();
		
		parentOpts.add("--id=feed_repo");
		parentOpts.add("--clone_id=parent_clone_repo");
		parentOpts.add("--feed=parent");
		parentOpts.add("-F");

		originOpts.add("--id=feed_repo");
		originOpts.add("--clone_id=origin_clone_repo");
		originOpts.add("--feed=origin");
		originOpts.add("-F");

		feedlessOpts.add("--id=feedless_repo");
		feedlessOpts.add("--clone_id=feedless_clone_repo");
		feedlessOpts.add("--feed=none");
		feedlessOpts.add("-F");

		data.add(Arrays.asList(new Object[]{parentOpts, "parent"}));		
		data.add(Arrays.asList(new Object[]{originOpts, "origin"}));		
		data.add(Arrays.asList(new Object[]{feedlessOpts, "feedless"}));		

		return data;
	}

	@DataProvider(name="localConsumerRepoData")
	public Object[][] localConsumerRepoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalConsumerRepoData());
	}
	public List<List<Object>> getLocalConsumerRepoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		// (¬_¬) this is rather crude... manual O(n^2) implementation
		//       curse you java! where's my zip()
		for (List<Object> consumer : getConsumerData()) {
			for (List<Object> repo : getLocalRepoData()) {
				data.add(Arrays.asList(new Object[]{repo.get(0), repo.get(1), (String)consumer.get(0), (PulpTasks)consumer.get(1)}));
			}
		}
		return data;
	}
}
