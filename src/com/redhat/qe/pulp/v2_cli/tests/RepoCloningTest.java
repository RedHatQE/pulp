package com.redhat.qe.pulp.v2_cli.tests;

import java.util.logging.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Hashtable;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;

import com.redhat.qe.pulp.v2_cli.base.PulpTestScript;
import com.redhat.qe.pulp.v2_cli.tasks.PulpTasks;
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
		newRepoData.add("--repo-id=feed_repo");
		newRepoData.add("--feed=http://qeblade20.rhq.lab.eng.bos.redhat.com/pub/updates/");
		servertasks.createTestRepo(newRepoData);

		servertasks.syncTestRepo("feed_repo", true);
	}

	@BeforeClass (groups={"testRepoCloning"})
	public void createFeedlessRepo() {
		ArrayList<String> newRepoData = new ArrayList<String>();
		newRepoData.add("--repo-id=feedless_repo");
		servertasks.createTestRepo(newRepoData);

		servertasks.syncTestRepo("feedless_repo", true);
	}

	@BeforeClass (groups={"testRepoCloning"})
	public void createCloneRepos() {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(2)).replace("--to-repo-id=", "");
			servertasks.createTestRepo(repoId);
		}
	}

	@Test (groups={"testRepoCloning"}, dataProvider="localRepoData")
	public void createClones(ArrayList<String> repoOpts, String cloneType) {
		servertasks.cloneTestRepo(repoOpts);
	}

	@Test (groups={"testRepoCloning"}, dataProvider="localRepoData", dependsOnMethods={"createClones"})
	public void verifyCloneCreation(ArrayList<String> repoOpts, String cloneType) {
		String clonedRepoId = repoOpts.get(2).replace("--to-repo-id=", "");
		ArrayList<Hashtable<String, ArrayList<String>>> filteredResult = this.match(servertasks.listRepo(true), ":");
		Hashtable<String, ArrayList<String>> result = pulpAbs.findRepo(filteredResult, clonedRepoId, "Repo Id");
		Assert.assertEquals(result.get("Id").get(0), clonedRepoId, "comparing repos id: "+ result.get("Id").get(0) +" to " + clonedRepoId);
	}

	@Test (groups={"testRepoCloning"}, dataProvider="localRepoData", dependsOnMethods={"verifyCloneCreation"})
	public void publishCloneRepos(ArrayList<String> repoOpts, String cloneType) {
		String clonedRepoId = repoOpts.get(2).replace("--to-repo-id=", "");
		servertasks.publishRepo(clonedRepoId);
	}

	@Test (groups={"testRepoCloning"}, dataProvider="localRepoData", dependsOnMethods={"publishCloneRepos"})
	public void bindClonedRepo(ArrayList<String> repoOpts, String cloneType) {
		String clonedRepoId = repoOpts.get(2).replace("--to-repo-id=", "");
		
		for (List<Object> consumer : getConsumerData()) {
			String consumerId = (String)consumer.get(0);
			PulpTasks task = (PulpTasks)consumer.get(1);

			task.bindConsumer(consumerId, clonedRepoId, true);
		}
	}

	@Test (groups={"testRepoCloning"}, dataProvider="localRepoData", dependsOnMethods={"bindClonedRepo"})
	public void repoYumCheck(ArrayList<String> repoOpts, String cloneType) {
		String clonedRepoId = repoOpts.get(2).replace("--to-repo-id=", "");
		int pkg_count = getYumPkgCount(servertasks.yumRepoList(), clonedRepoId);
		//int reportedPkgCount = pulpAbs.getRepoPkgCountFromStatus(pulpAbs.match(servertasks.listRepoStatus(clonedRepoId)), clonedRepoId);
		ArrayList<Hashtable<String, ArrayList<String>>> filteredResult = this.match(servertasks.listRepo(true), ":");
		Hashtable<String, ArrayList<String>> result = pulpAbs.findRepo(filteredResult, clonedRepoId, "Id");
		int reportedPkgCount = Integer.parseInt(result.get("Content Unit Count").get(0));
		Assert.assertEquals(reportedPkgCount, pkg_count, "Verifying package count reported by yum and pulp is identical.");
	}

   	@Test (groups={"testRepoCloning"}, dependsOnMethods={"repoYumCheck"})
	public void adjustRootFeedlessRepo() {
		servertasks.uploadContent("feedless_repo", "feedless-1.0-1.noarch.rpm", "http://qeblade20.rhq.lab.eng.bos.redhat.com/pub/updates/feedless-1.0-1.noarch.rpm", tmpRpmDir, true);
		// Temporary workaround for content bz 752098
		/*
		while (!servertasks.getMetadataStatus("feedless_repo").contains("finished")) {
			try {
				Thread.currentThread().sleep(10000);
			} catch (Exception e) {}
		}
		*/
	}
 
   	@Test (groups={"testRepoCloning"}, dataProvider="localRepoData", dependsOnMethods={"adjustRootFeedlessRepo"})
	public void checkClonedRepo(ArrayList<String> repoOpts, String cloneType) {
		String clonedRepoId = repoOpts.get(2).replace("--to-repo-id=", "");
		String feed = repoOpts.get(2).replace("--feed=", "");

		servertasks.cleanCache();
		if (!(feed.equals("none"))) {
			servertasks.syncTestRepo(clonedRepoId, true);
		}

		int pkg_count = getYumPkgCount(servertasks.yumRepoList(), clonedRepoId);
		Hashtable<String, ArrayList<String>> result2 = pulpAbs.findRepo(this.match(servertasks.listRepo(true), ":"), clonedRepoId, "Id");
		int reportedPkgCount = Integer.parseInt(result2.get("Content Unit Count").get(0));

		if (cloneType.equals("parent")) {
			// things should not have changed as only the feedless repo got mod
			Hashtable<String, ArrayList<String>> result = pulpAbs.findRepo(this.match(servertasks.listRepo(true), ":"), "feed_repo", "Id");
			int reportedPkgCountParent = Integer.parseInt(result.get("Content Unit Count").get(0)) - 1;
			Assert.assertTrue((reportedPkgCountParent == pkg_count && reportedPkgCountParent == reportedPkgCount), "Comparing unchanged pkg counts.");
		}
		else if (cloneType.equals("feedless") || cloneType.equals("origin")) {
			ArrayList<Hashtable<String, ArrayList<String>>> filteredResult = this.match(servertasks.listRepo(true), ":");
			int reportedPkgCountParent = pulpAbs.getRepoPkgCountFromStatus(filteredResult, "feedless_repo");
			Assert.assertEquals(reportedPkgCountParent, -1, "Comparing feedless parent pkg counts.");
			Assert.assertTrue((reportedPkgCount == 0 && pkg_count == 0), "Comparing feedless clone pkg counts to 0 to make sure it did not change.");
		}
	}

   	@Test (groups={"testRepoCloning"}, dataProvider="localRepoData", dependsOnMethods={"checkClonedRepo"})
	public void prepInstallTestPkg(ArrayList<String> repoOpts, String cloneType) {
		String clonedRepoId = repoOpts.get(2).replace("--to-repo-id=", "");
		for (List<Object> consumer : getConsumerData()) {
			//String consumerId = (String)consumer.get(0);
			PulpTasks task = (PulpTasks)consumer.get(1);

			task.unbindConsumer(clonedRepoId);
		}
	}

   	@Test (groups={"testRepoCloning"}, dataProvider="localConsumerRepoData", dependsOnMethods={"prepInstallTestPkg"})
	public void installTestPkg(ArrayList<String> repoOpts, String cloneType, String consumerId, PulpTasks task) {

		// this is dangerous
		String clonedRepoId = repoOpts.get(2).replace("--to-repo-id=", "");
		String pkgName = cloneType; // pkg name is the same as clone type
		String feed = repoOpts.get(2).replace("--feed=", "");

		if (cloneType.equals("parent")) {
			try {
				task.bindConsumer(consumerId, clonedRepoId, true);

				uninstallPkg(pkgName, task); // just in case things didn't clean up correctly
				String result = task.installPackage(pkgName, consumerId);
				Assert.assertTrue(result.contains("Install Succeeded"), "Verifying package installation was successful.");
			} catch (Exception e) {
			} finally {
				// cleanup
				uninstallPkg(pkgName, task);
				task.unbindConsumer(clonedRepoId);
			}
		}
		else {
			// for now, nothing since there's no pkg in this repo
			log.info("Skipping package installation since feedless repo has no pkg.");
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
			repoId = ((String)repoData.get(2)).replace("--to-repo-id=", "");
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
			ArrayList<Hashtable<String, ArrayList<String>>> filteredResult = this.match(servertasks.listRepo(true), ":");
			Hashtable<String, ArrayList<String>> result = pulpAbs.findRepo(filteredResult, repoId);
			Assert.assertNull(result.get("repoId"), "comparing repoId to null.");
		}
	}

	@DataProvider(name="localRepoData")
	public Object[][] localRepoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalRepoData());
	}
	public List<List<Object>> getLocalRepoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();

		ArrayList<String> feed_rpmOpts = new ArrayList<String>();
		ArrayList<String> feed_drpmOpts = new ArrayList<String>();
		ArrayList<String> feed_srpmOpts = new ArrayList<String>();
		ArrayList<String> feedless_rpmOpts = new ArrayList<String>();
		ArrayList<String> feedless_drpmOpts = new ArrayList<String>();
		ArrayList<String> feedless_srpmOpts = new ArrayList<String>();
		
		feed_rpmOpts.add("rpm");
		feed_rpmOpts.add("--from-repo-id=feed_repo");
		feed_rpmOpts.add("--to-repo-id=feed_rpm_clone_repo");

		feed_drpmOpts.add("drpm");
		feed_drpmOpts.add("--from-repo-id=feed_repo");
		feed_drpmOpts.add("--to-repo-id=feed_drpm_clone_repo");

		feed_srpmOpts.add("srpm");
		feed_srpmOpts.add("--from-repo-id=feed_repo");
		feed_srpmOpts.add("--to-repo-id=feed_srpm_clone_repo");

		feedless_rpmOpts.add("rpm");
		feedless_rpmOpts.add("--from-repo-id=feedless_repo");
		feedless_rpmOpts.add("--to-repo-id=feedless_rpm_clone_repo");

		feedless_drpmOpts.add("drpm");
		feedless_drpmOpts.add("--from-repo-id=feedless_repo");
		feedless_drpmOpts.add("--to-repo-id=feedless_drpm_clone_repo");

		feedless_srpmOpts.add("srpm");
		feedless_srpmOpts.add("--from-repo-id=feedless_repo");
		feedless_srpmOpts.add("--to-repo-id=feedless_srpm_clone_repo");

		data.add(Arrays.asList(new Object[]{feed_rpmOpts, "parent"}));		
		data.add(Arrays.asList(new Object[]{feed_drpmOpts, "origin"}));		
		data.add(Arrays.asList(new Object[]{feed_srpmOpts, "feedless"}));		
		data.add(Arrays.asList(new Object[]{feedless_rpmOpts, "feedless"}));		
		data.add(Arrays.asList(new Object[]{feedless_drpmOpts, "feedless"}));		
		data.add(Arrays.asList(new Object[]{feedless_srpmOpts, "feedless"}));		

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
