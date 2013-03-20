package com.redhat.qe.pulp.cli.tests;

import com.redhat.qe.pulp.cli.base.PulpTestScript;
import com.redhat.qe.pulp.cli.tasks.PulpTasks;
import com.redhat.qe.auto.testng.TestNGUtils;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;

import com.redhat.qe.Assert;

/*
 * There's quite a few interesting scenario that can be tested on this involving pkgs installed/not installed
 * on the system and errata update list. Now add on top of that, multiple erratum, oh my!
 *
 *            Installed
 *             T     F
 *          +-----+-----+
 *        T |  1  |  3  |
 * Update   +-----|-----+
 *        F |  2  |  3  |
 *          +-----+-----+
 *
 * 1. If pkg is installed, it's expected the pkg will be upgraded.
 * 2. If pkg is installed, but update is not in errata, then it's expected the pkg will not be updated.
 * 3. If pkg is not installed, it's expected the pkg will not be install nor upgraded.
 */

public class ErrataTest extends PulpTestScript {
	private ArrayList<Hashtable<String, String>> trackedPkgs = new ArrayList<Hashtable<String, String>>();
	public ErrataTest() {
		super();

		for (List<Object> pkg : getPkgData()) {
			Hashtable<String, String> pkgEntry = new Hashtable<String, String>();
			pkgEntry.put("name", (String)pkg.get(3));
			pkgEntry.put("installed", "true");
			pkgEntry.put("updateAvail", "false");
			
			trackedPkgs.add(pkgEntry);
		}
		
		Hashtable<String, String> rules = new Hashtable<String, String>();
		rules.put("errataId", "^Id[\\s]+([a-zA-Z0-9\\-]+)[\\s]*$");
		rules.put("errataPkg", "^Packages Effected[\\s]+([a-zA-Z0-9\\_\\-\\[\\]\\'\\,\\.\\s]+)[\\s]*$");

		pulpAbs.appendRegexCriterion(rules);
	}

	@BeforeClass (groups={"testErrata"}) 
	public void createTestRepo() {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			servertasks.createTestRepo(repoData);
		}
	}

	@BeforeClass (groups={"testErrata"}, dependsOnMethods={"createTestRepo"})
	public void uploadTestPkgs() {
		for (List<Object> repoPkg : getUploadRepoPkgData()) {
			// Temporary workaround for content bz 752098
			this.getMetadataStatus((String)repoPkg.get(0));
			servertasks.uploadContent((String)repoPkg.get(0), (String)repoPkg.get(1), (String)repoPkg.get(2), (String)repoPkg.get(3), true);
		}
	}

	@BeforeClass (groups={"testErrata"}, dependsOnMethods={"uploadTestPkgs"})
	public void bindTestPkgRepo() {
		for (List<Object> repoConsumer : getUploadRepoConsumerData()) {
			String repoId = (String)repoConsumer.get(0);
			String consumerId = (String)repoConsumer.get(1);
			PulpTasks task = (PulpTasks)repoConsumer.get(2);
			task.bindConsumer(consumerId, repoId, true);
		}
	}

	@BeforeClass (groups={"testErrata"}, dependsOnMethods={"bindTestPkgRepo"})
	public void installTestPkgs() {
		for (Hashtable<String, String> entry : trackedPkgs) {
			for (List<Object> consumer : getConsumerData()) {
				String consumerId = (String)consumer.get(0);
				PulpTasks task = (PulpTasks)consumer.get(1);
				task.installPackage(entry.get("name"), consumerId);
			}
		}
	}

	@BeforeClass (groups={"testErrata"}, dependsOnMethods={"installTestPkgs"})
	public void bindTestErrataRepo() {
		for (List<Object> repoConsumer : getUploadRepoConsumerData()) {
			String repoId = (String)repoConsumer.get(0);
			String consumerId = (String)repoConsumer.get(1);
			PulpTasks task = (PulpTasks)repoConsumer.get(2);
			task.unbindConsumer(consumerId, repoId);
		}
		//servertasks.unbindConsumer(consumerId, "errata_pkg_upload_repo");
		//clienttasks.unbindConsumer(consumerId+"_remote", "errata_pkg_upload_repo");
		for (List<Object> repoConsumer : getRepoConsumerData()) {
			String repoId = (String)repoConsumer.get(0);
			String consumerId = (String)repoConsumer.get(1);
			PulpTasks task = (PulpTasks)repoConsumer.get(2);
			task.bindConsumer(consumerId, repoId, true);
		}
		//servertasks.bindConsumer(consumerId, "errata_test_repo", true);
		//clienttasks.bindConsumer(consumerId+"_remote", "errata_test_repo", true);
	}

	@BeforeClass (groups={"testErrata"}, dependsOnMethods={"bindTestErrataRepo"})
	public void syncTestErrataRepo() {
		for (List<Object> repoConsumer : getRepoConsumerData()) {
			String repoId = (String)repoConsumer.get(0);
			String consumerId = (String)repoConsumer.get(1);
			PulpTasks task = (PulpTasks)repoConsumer.get(2);
			task.syncTestRepo(repoId, true);
		}
		//servertasks.syncTestRepo("errata_test_repo", true);
	}

	@AfterClass (groups={"testErrata"}, alwaysRun=true)
	public void uninstallTestPkgs() {
		// I wonder if we should roll back errata installs first? Can we even do that?
		for (Hashtable<String, String> entry : trackedPkgs) {
			uninstallPkg(entry.get("name"), servertasks);
			uninstallPkg(entry.get("name"), clienttasks);
		}
	}

	@AfterClass (groups={"testErrata"}, alwaysRun=true, dependsOnMethods={"uninstallTestPkgs"})
	public void unbindTestRepo() {
		for (List<Object> repoConsumer : getRepoConsumerData()) {
			String repoId = (String)repoConsumer.get(0);
			String consumerId = (String)repoConsumer.get(1);
			PulpTasks task = (PulpTasks)repoConsumer.get(2);
			task.unbindConsumer(consumerId, repoId);
		}
		//servertasks.unbindConsumer(consumerId, "errata_test_repo");
		//clienttasks.unbindConsumer(consumerId+"_remote", "errata_test_repo");
	}

	@AfterClass (groups={"testErrata"}, alwaysRun=true, dependsOnMethods={"unbindTestRepo"})
	public void deleteTestRepo() {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(0)).replace("--id=", "");
			servertasks.deleteTestRepo(repoId);
		}
	}

	@AfterClass (groups={"testErrata"}, alwaysRun=true, dependsOnMethods={"deleteTestRepo"})
	public void verifyDeleteTestRepo() {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = (String)repoData.get(0);
			ArrayList<Hashtable<String, ArrayList<String>>> filteredResult = this.match(servertasks.listRepo(true), "   ");
			Hashtable<String, ArrayList<String>> result = pulpAbs.findRepo(filteredResult, repoId);
			Assert.assertEquals(result.size(), 0, "comparing repoId to null.");
		}
	}

	/*  Errata creation/deletion will be implemented soon. 
 	
	@Test (groups={"testErrata"}, dataProvider="errataData")
	public void createErrata() {
	}

	@Test (groups={"testErrata"}, dataProvider="errataData")
	public void deleteErrata() {
	}

	*/

	@Test (groups={"testErrata", "consumerOnly"}, dataProvider="consumerData")
	public void updateConsumerPkgProfile(String consumerId, PulpTasks task) {
		task.updatePkgInfo();
	}

	@Test (groups={"testErrata", "consumerOnly"}, dataProvider="errataData", dependsOnMethods={"updateConsumerPkgProfile"})
	public void listAvailErrataByConsumer(String repoId, String consumerId, String errataId, PulpTasks task) {
		task.listErrataByConsumer(consumerId);
	}

	@Test (groups={"testErrata", "consumerOnly"}, dataProvider="errataData", dependsOnMethods={"updateConsumerPkgProfile"})
	public void listAvailErrataByRepo(String repoId, String consumerId, String errataId, PulpTasks task) {
		task.listErrataByRepo(repoId);
	}

	@Test (groups={"testErrata", "consumerOnly"}, dataProvider="errataData", dependsOnMethods={"listAvailErrataByConsumer", "listAvailErrataByRepo"})
	public void verifyErrataInfo(String repoId, String consumreId, String errataId, PulpTasks task) {
		ArrayList<Hashtable<String, ArrayList<String>>> filteredResult = this.match(task.errataInfo(errataId), "   ");
		String[] pkgUpdateList = decodePkg(filteredResult.get(0).get("Packages Effected").get(0));

		for (String pkg : pkgUpdateList) {
			for (Hashtable<String, String> known_pkgs : trackedPkgs) {
				if (pkg.contains(known_pkgs.get("name"))) {
					known_pkgs.put("updateAvail", "true");
				}
			}
		}
	}

	@Test (groups={"testErrata", "consumerOnly"}, dataProvider="errataData", dependsOnMethods={"verifyErrataInfo"})
	public void installErrataConsumer(String repoId, String consumerId, String errataId, PulpTasks task) {
		task.cleanCache();
		String result = task.installErrata(consumerId, errataId);
		Assert.assertTrue(result.contains("Errata applied to"), "Verifying pulp reports errata installation was successful.");
	}

	@Test (groups={"testErrata", "consumerOnly"}, dependsOnMethods={"installErrataConsumer"})
	public void verifyInstallErrataConsumer() {
		verifyInstallErrata();
	}

	@Test (groups={"testErrata", "consumerOnly"}, dependsOnMethods={"verifyInstallErrataConsumer"})
	public void prep() {
		uninstallTestPkgs();
		unbindTestRepo();
		bindTestPkgRepo();
		installTestPkgs();
		bindTestErrataRepo();
	}

	@Test (groups={"testErrata", "consumerGroup"}, dependsOnMethods={"prep"})
	public void installErrataConsumerGroup() {
		servertasks.cleanCache();
		for (List<Object> grp : getConsumerGroupData()) {
			String consumerGroupId = (String)grp.get(0);
			String result = servertasks.installGroupErrata(consumerGroupId, "RHEA-2010:9999");
			Assert.assertTrue(result.contains("Install Summary"), "Verifying pulp reports errata installation was successful.");
		}
	}

	@Test (groups={"testErrata", "consumerGroup"}, dependsOnMethods={"installErrataConsumerGroup"})
	public void verifyInstallErrataConsumerGroup() {
		verifyInstallErrata();
	}

	public void verifyInstallErrata() {
		ArrayList<String> scenarioOneData = new ArrayList<String>();
		ArrayList<String> scenarioTwoData = new ArrayList<String>();
		ArrayList<String> scenarioThreeData = new ArrayList<String>();

		for (Hashtable<String, String> known_pkgs : trackedPkgs) {
 			// If pkg is installed, it's expected the pkg will be upgraded.
			if (Boolean.parseBoolean(known_pkgs.get("availUpdate")) &&
				Boolean.parseBoolean(known_pkgs.get("installed"))) {
				scenarioOneData.add(known_pkgs.get("name"));
			}
 			// If pkg is installed, but update is not in errata, then it's expected the pkg will not be updated.
			else if (!Boolean.parseBoolean(known_pkgs.get("availUpdate")) &&
				Boolean.parseBoolean(known_pkgs.get("installed"))) {
				scenarioTwoData.add(known_pkgs.get("name"));
			}
 			// If pkg is not installed, it's expected the pkg will not be install nor upgraded.
			else {
				scenarioThreeData.add(known_pkgs.get("name"));
			}
		}

		// Scenario 1: Pkgs installed and have updates in the errata
		for (String pkgName : scenarioOneData) {
			String queryResult = servertasks.packageSearch(pkgName);
			String[] expectedResult = servertasks.getRepoPackageList("errata_test_repo").split("packages in errata_test_repo:\n")[1].split("\n"); 
			boolean result = false;
			// TODO: Is this backward? Should comparison be the other way around?
			for (String pkg : expectedResult) {
				if (queryResult.trim().matches(pkg.trim())) {
					result = true;
					break;
				}
			}

			Assert.assertTrue(result, "Verifying the right version of " + pkgName + " is installed on the system.");
		}
		
		// Scenario 3: Pkg is not installed
		for (String pkgName : scenarioThreeData) {
			String queryResult = servertasks.packageSearch(pkgName);
			Assert.assertEquals(queryResult, "", "Verifying the package was not mistakenly installed.");
		}
	}

	@DataProvider(name="localRepoData")
	public Object[][] localRepoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalRepoData());
	}
	public List<List<Object>> getLocalRepoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();

		ArrayList<String> repoOpts = new ArrayList<String>();
		repoOpts.add("--id=errata_test_repo");
		repoOpts.add("--feed=" + System.getProperty("automation.resources.location") + "updates/");
		data.add(Arrays.asList(new Object[]{repoOpts}));

		repoOpts = new ArrayList<String>();
		repoOpts.add("--id=errata_pkg_upload_repo");
		data.add(Arrays.asList(new Object[]{repoOpts}));
		return data;
	}

	@DataProvider(name="uploadRepoPkgData")
	public Object[][] uploadRepoPkgData() {
		return TestNGUtils.convertListOfListsTo2dArray(getUploadRepoPkgData());
	}
	public List<List<Object>> getUploadRepoPkgData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		// (¬_¬) this is rather crude... manual O(n^2) implementation
		//       curse you java! where's my zip()
		for (List<Object> repo : getLocalRepoData()){
			ArrayList repoData = (ArrayList)repo.get(0);
			if (repoData.size() < 2) {
				for (List<Object> pkg : getPkgData()) {
					String repoId = ((String)repoData.get(0)).replace("--id=", "");
					data.add(Arrays.asList(new Object[]{repoId, pkg.get(0), pkg.get(1), pkg.get(2)}));
				}
			}
		}
		return data;
	}

	@DataProvider(name="uploadRepoConsumerData")
	public Object[][] uploadRepoConsumerData() {
		return TestNGUtils.convertListOfListsTo2dArray(getUploadRepoConsumerData());
	}
	public List<List<Object>> getUploadRepoConsumerData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		// (¬_¬) this is rather crude... manual O(n^2) implementation
		//       curse you java! where's my zip()
		for (List<Object> repo : getLocalRepoData()){
			ArrayList repoData = (ArrayList)repo.get(0);
			if (repoData.size() < 2) {
				for (List<Object> consumer : getConsumerData()) {
					String repoId = ((String)repoData.get(0)).replace("--id=", "");
					data.add(Arrays.asList(new Object[]{repoId, consumer.get(0), consumer.get(1)}));
				}
			}
		}
		return data;
	}

	@DataProvider(name="repoConsumerData")
	public Object[][] repoConsumerData() {
		return TestNGUtils.convertListOfListsTo2dArray(getRepoConsumerData());
	}
	public List<List<Object>> getRepoConsumerData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		// (¬_¬) this is rather crude... manual O(n^2) implementation
		//       curse you java! where's my zip()
		for (List<Object> repo : getLocalRepoData()){
			ArrayList repoData = (ArrayList)repo.get(0);
			if (repoData.size() > 1) {
				for (List<Object> consumer : getConsumerData()) {
					String repoId = ((String)repoData.get(0)).replace("--id=", "");
					data.add(Arrays.asList(new Object[]{repoId, consumer.get(0), consumer.get(1)}));
				}
			}
		}
		return data;
	}
	
	@DataProvider(name="errataData")
	public Object[][] errataData() {
		return TestNGUtils.convertListOfListsTo2dArray(getErrataData());
	}
	public List<List<Object>> getErrataData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		for (List<Object> consumer : getConsumerData()) {
			data.add(Arrays.asList(new Object[]{"errata_test_repo", (String)consumer.get(0), "RHEA-2010:9999", (PulpTasks)consumer.get(1)}));
		}
		return data;
	}
}
