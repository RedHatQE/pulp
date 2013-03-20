package com.redhat.qe.pulp.v2_cli.tests;

import com.redhat.qe.pulp.v2_cli.base.PulpTestScript;
import com.redhat.qe.pulp.v2_cli.tasks.PulpTasks;

import com.redhat.qe.auto.testng.TestNGUtils;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Hashtable;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;

import com.redhat.qe.Assert;

public class PackageTest extends PulpTestScript {
	public PackageTest() {
		super();
	}

	@BeforeClass (groups="testPackage") 
	public void updateTestRepo() {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			servertasks.createTestRepo(repoData);
		}
	}

	@BeforeClass (groups="testPackage", dependsOnMethods={"updateTestRepo"}) 
	public void uploadPackage() {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(0)).replace("--repo-id=", "");
			for (List<Object> pkg : getPkgData()) {
				//servertasks.uploadContent(repoId, (String)pkg.get(0), (String)pkg.get(1), (String)pkg.get(2), true);
				// Temporary workaround for content bz 752098
				servertasks.uploadContent(repoId, (String)pkg.get(0), (String)pkg.get(1), (String)pkg.get(2), false);

				// Temporary workaround for content bz 752098
				/*
				while (!servertasks.getMetadataStatus(repoId).contains("finished")) {
					try {
						Thread.currentThread().sleep(10000);
					} catch (Exception e) {}
				}
				*/
				servertasks.publishRepo(repoId);
			}
		}
	}

	@BeforeClass (groups="testPackage", dependsOnMethods={"uploadPackage"})
	public void bind() {
		for (List<Object> consumerRepo : getLocalConsumerRepoData()) {
			String repoId = (String)consumerRepo.get(0);
			String consumerId = (String)consumerRepo.get(1);
			PulpTasks task = (PulpTasks)consumerRepo.get(2);
			
			task.bindConsumer(consumerId, repoId, true);
		}
	}

	@AfterClass (groups="testPackage", alwaysRun=true)
	public void unbind() {
		for (List<Object> consumerRepo : getLocalConsumerRepoData()) {
			String repoId = (String)consumerRepo.get(0);
			String consumerId = (String)consumerRepo.get(1);
			PulpTasks task = (PulpTasks)consumerRepo.get(2);
			
			task.unbindConsumer(repoId);
		}
	}

	@AfterClass (groups="testPackage", alwaysRun=true, dependsOnMethods={"unbind"})
	public void teardownUploadRepo() {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(0)).replace("--repo-id=", "");
			servertasks.deleteTestRepo(repoId);
		}
	}

	@AfterClass (groups={"testPackage"}, alwaysRun=true, dependsOnMethods={"teardownUploadRepo"})
	public void verifyDeleteTestRepo() {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = (String)repoData.get(0);
			ArrayList<Hashtable<String, ArrayList<String>>> filteredResult = this.match(servertasks.listRepo(true), ":");
			Hashtable<String, ArrayList<String>> result = pulpAbs.findRepo(filteredResult, repoId);
			Assert.assertNull(result.get("Id"), "comparing repoId to null.");
		}
	}

	@AfterClass (groups="testPackage", alwaysRun=true)
	public void teardownInstalledPkgs() {
		Object[][] data = pkgInstallData();
		for (Object[] entry : data) {
			uninstallPkg((String)entry[0], servertasks);
			uninstallPkg((String)entry[0], clienttasks);
		}
		data = unsignedPkgInstallData();
		for (Object[] entry : data) {
			uninstallPkg((String)entry[0], servertasks);
			uninstallPkg((String)entry[0], clienttasks);
		}
	}

	@Test (groups={"testPackage", "pkgInstall"}, dataProvider="pkgInfoData")
	public void verifyPkgInfo(String repoId, String queryName) {
		servertasks.packageList(repoId, queryName);
	}

	@Test (groups={"testPackage", "consumerInstall"}, dataProvider="pkgInstallData", dependsOnMethods={"verifyPkgInfo"})
	public void installPkg(String pkgName, String consumerId, PulpTasks task) {
		uninstallPkg(pkgName, task); // just in case the previous test left us in a bad state
		String result = task.installPackage(pkgName, consumerId);
		//Assert.assertTrue(result.contains("installed on "+consumerId), "Verifying package installation was successful.");
	}

	@Test (groups={"testPackage", "consumerInstall"}, dataProvider="pkgInstallData", dependsOnMethods={"installPkg"})
	public void verifyInstallPkg(String pkgName, String consumerId, PulpTasks task) {
		String result = task.packageSearch(pkgName);
		Assert.assertTrue(result.contains(pkgName), "Determining if rpm is correctly installed.");
		uninstallPkg(pkgName, task);
	}

/*
	@Test (groups={"testPackage", "consumerGroupInstall"}, dataProvider="consumerGroupInstallPkg", dependsOnMethods={"verifyPkgInfo"}, dependsOnGroups={"consumerInstall"})
	public void installPkgConsumerGroup(String pkgName, String consumerGroupId) {
		// This really shouldn't happen anymore
		//uninstallPkg(pkgName); // just in case the previous test left us in a bad state
		servertasks.installGroupPackage(pkgName, consumerGroupId);
	}

	@Test (groups={"testPackage", "consumerGroupInstall"}, dataProvider="consumerGroupInstallPkg", dependsOnMethods={"installPkgConsumerGroup"}, dependsOnGroups={"consumerInstall"})
	public void verifyConsumerGroupInstallPkg(String pkgName, String consumerGroupId) {
		String result = servertasks.packageSearch(pkgName);
		Assert.assertTrue(result.contains(pkgName), "Determining if rpm is correctly installed.");
		groupUninstallPkg(pkgName);
	}
*/

	// Negative Tests
	@Test (groups={"testPackage"}, dataProvider="unsignedPkgRepoData")
	public void checkUnsignedPkgOutput(String repoId, String rpmName, String rpmUrl, String tmpDir) {
		// check error code and suggestion from -v
		String stdout = servertasks.uploadContent(repoId, rpmName, rpmUrl, tmpDir, false);
		Assert.assertFalse(stdout.contains("Traceback"), "Making sure stdout does not contain any traceback.");
		//Assert.assertTrue(stdout.contains("--nosig"), "Making sure stdout contains --nosig suggestion.");
	}

	@Test (groups={"testPackage"}, dataProvider="unsignedPkgRepoData", dependsOnMethods={"checkUnsignedPkgOutput"})
	public void uploadUnsignedPkg(String repoId, String rpmName, String rpmUrl, String tmpDir) {
		servertasks.uploadUnsignedContent(repoId, rpmName, rpmUrl, tmpDir);
	}

	@Test (groups={"testPackage"}, dataProvider="unsignedPkgInstallData", dependsOnMethods={"uploadUnsignedPkg"})
	public void installUnsignedPkg(String pkgName, String consumerId, PulpTasks task) {
		uninstallPkg(pkgName, task); // just in case the previous test left us in a bad state
		String result = task.installPackage(pkgName, consumerId);
		//Assert.assertTrue(result.contains("installed on "+consumerId), "Verifying package installation was successful.");
	}

	// ===========================================================================
	@DataProvider(name="localRepoData") 
	public Object[][] localRepoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalRepoData());
	}
	public List<List<Object>> getLocalRepoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();

		ArrayList<String> repoOpts = new ArrayList<String>();
		repoOpts.add("--repo-id=package_test_repo");
		//repoOpts.add("--regenerate-metadata=true");
		data.add(Arrays.asList(new Object[]{repoOpts}));

		return data;
	}

	@DataProvider(name="consumerRepoData")
	public Object[][] consumerRepoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalConsumerRepoData());
	}
	public List<List<Object>> getLocalConsumerRepoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		// (¬_¬) this is rather crude... manual O(n^2) implementation
		//       curse you java! where's my zip()
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(0)).replace("--repo-id=", "");
			for (List<Object> consumer : getConsumerData()) {
				data.add(Arrays.asList(new Object[]{repoId, consumer.get(0), consumer.get(1)}));
			}
		}
		return data;
	}

	@DataProvider(name="pkgInfoData")
	public Object[][] pkgInfoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getPkgInfoData());
	}
	
	protected List<List<Object>> getPkgInfoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		for (List<Object> pkg : getPkgData()) {
			data.add(Arrays.asList(new Object[]{"package_test_repo", pkg.get(3)}));
		}
		return data;
	}

	@DataProvider(name="pkgInstallData")
	public Object[][] pkgInstallData() {
		return TestNGUtils.convertListOfListsTo2dArray(getInstallData());
	}
	public List<List<Object>> getInstallData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		// (¬_¬) this is rather crude... manual O(n^2) implementation
		//       curse you java! where's my zip()
		for (List<Object> consumer : getConsumerData()) {
			for (List<Object> pkg : getPkgData()) {
				data.add(Arrays.asList(new Object[]{(String)pkg.get(3), (String)consumer.get(0), (PulpTasks)consumer.get(1)}));
			}
		}
		return data;
	}

	@DataProvider(name="consumerGroupInstallPkg")
	public Object[][] consumerGroupInstallPkg() {
		return TestNGUtils.convertListOfListsTo2dArray(getGroupInstallData());
	}
	public List<List<Object>> getGroupInstallData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		for (List<Object> consumerGroup : getConsumerGroupData()) {
			String consumerGroupId = (String)consumerGroup.get(0);
			for (List<Object> pkg : getPkgData()) {
				data.add(Arrays.asList(new Object[]{(String)pkg.get(3), consumerGroupId}));
			}
		}
		return data;
	}

	@DataProvider(name="unsignedPkgData")
	public Object[][] unsignedPkgData() {
		return TestNGUtils.convertListOfListsTo2dArray(getUnsignedPkgData());
	}
	public List<List<Object>> getUnsignedPkgData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		data.add(Arrays.asList(new Object[]{"unsigned-1.0-1.noarch.rpm", "http://qeblade20.rhq.lab.eng.bos.redhat.com/pub/unsigned-1.0-1.noarch.rpm", tmpRpmDir, "unsigned"}));
		return data;
	}

	@DataProvider(name="unsignedPkgRepoData")
	public Object[][] unsignedPkgRepoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getUnsignedPkgRepoData());
	}
	public List<List<Object>> getUnsignedPkgRepoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		for (List<Object> pkg : getUnsignedPkgData()) {
			data.add(Arrays.asList(new Object[]{"package_test_repo", pkg.get(0), pkg.get(1), pkg.get(2)}));
		}
		return data;
	}

	@DataProvider(name="unsignedPkgInstallData")
	public Object[][] unsignedPkgInstallData() {
		return TestNGUtils.convertListOfListsTo2dArray(getUnsignedInstallData());
	}
	public List<List<Object>> getUnsignedInstallData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		// (¬_¬) this is rather crude... manual O(n^2) implementation
		//       curse you java! where's my zip()
		for (List<Object> consumer : getConsumerData()) {
			for (List<Object> pkg : getUnsignedPkgData()) {
				data.add(Arrays.asList(new Object[]{(String)pkg.get(3), (String)consumer.get(0), (PulpTasks)consumer.get(1)}));
			}
		}
		return data;
	}
}
