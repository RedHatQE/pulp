package com.redhat.qe.pulp.v2_cli.tests;

import com.redhat.qe.pulp.v2_cli.base.PulpTestScript; 
import com.redhat.qe.pulp.v2_cli.tasks.PulpTasks;

import com.redhat.qe.auto.testng.TestNGUtils;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Hashtable;
import java.io.IOException;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;

import com.redhat.qe.Assert;

public class PackageGroupTest extends PulpTestScript {
	public PackageGroupTest() {
		super();

		Hashtable<String, String> rules = new Hashtable<String, String>();
		
		rules.put("pkgGroupName", "^Name[\\s]+([a-zA-Z0-9\\_\\-]+)[\\s]*$");
		rules.put("pkgGroupId", "^Id[\\s]+([a-zA-Z0-9\\_\\-]+)[\\s]*$");
		rules.put("pkgGroupDefaultPkg", "^[\\s]*Default Package Names:[\\s]+([a-zA-Z0-9\\.\\_\\-\\[\\]\\'\\,\\/\\s]+)[\\s]*$");
		rules.put("pkgGroupMandatoryPkg", "^[\\s]*Mandatory Packages Names:[\\s]+([a-zA-Z0-9\\.\\_\\-\\[\\]\\'\\/\\,]+)[\\s]*$");
		rules.put("pkgGroupOptionalPkg", "^[\\s]*Optional Package Names:[\\s]+([a-zA-Z0-9\\.\\_\\-\\[\\]\\'\\,\\/]+)[\\s]*$");
		rules.put("pkgGroupConditionalPkg", "^[\\s]*Conditional Package Names:[\\s]+([a-zA-Z0-9\\.\\_\\-\\[\\]\\'\\/\\,]+)[\\s]*$");

		pulpAbs.appendRegexCriterion(rules);
	}

	@BeforeClass (groups="testPackageGroup") 
	public void createTestRepo() {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			servertasks.createTestRepo(repoData);
		}
	}

	@BeforeClass (groups="testPackageGroup", dependsOnMethods={"createTestRepo"}) 
	public void uploadPackage() {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(0)).replace("--repo-id=", "");
			for (List<Object> pkg : getLocalPkgData()) {
				servertasks.uploadContent(repoId, (String)pkg.get(0), (String)pkg.get(1), (String)pkg.get(2), true);
				// Temporary workaround for content bz 752098
				/*
				while (!servertasks.getMetadataStatus(repoId).contains("finished")) {
					try {
						Thread.currentThread().sleep(10000);
					} catch (Exception e) {}
				}
				*/
			}
		}
	}

	@BeforeClass (groups="testPackageGroup", dependsOnMethods={"uploadPackage"})
	public void bind() {
		for (List<Object> consumerRepo : getLocalConsumerRepoData()) {
			String repoId = (String)consumerRepo.get(0);
			String consumerId = (String)consumerRepo.get(1);
			PulpTasks task = (PulpTasks)consumerRepo.get(2);
			
			task.bindConsumer(consumerId, repoId, true);
		}
	}

	@AfterClass (groups="testPackageGroup", alwaysRun=true)
	public void unbind() {
		for (List<Object> consumerRepo : getLocalConsumerRepoData()) {
			String repoId = (String)consumerRepo.get(0);
			String consumerId = (String)consumerRepo.get(1);
			PulpTasks task = (PulpTasks)consumerRepo.get(2);
			
			task.unbindConsumer(repoId);
		}
	}

	@AfterClass (groups="testPackageGroup", alwaysRun=true, dependsOnMethods={"unbind"})
	public void teardownUploadRepo() {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(0)).replace("--repo-id=", "");
			servertasks.deleteTestRepo(repoId);
		}
	}

	@AfterClass (groups={"testPackageGroup"}, alwaysRun=true, dependsOnMethods={"teardownUploadRepo"})
	public void verifyDeleteTestRepo() {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = (String)repoData.get(0);
			ArrayList<Hashtable<String, ArrayList<String>>> filteredResult = this.match(servertasks.listRepo(true), ":");
			Hashtable<String, ArrayList<String>> result = pulpAbs.findRepo(filteredResult, repoId);
			Assert.assertNull(result.get("Id").get(0), "comparing repoId to null.");
		}
	}

	@AfterClass (groups="testPackageGroup", alwaysRun=true)
	public void teardownInstalledPkgs() {
		Object[][] data = pkgData();
		for (Object[] entry : data) {
			uninstallPkg((String)entry[3], servertasks);
			uninstallPkg((String)entry[3], clienttasks);
		}
	}

	@Test (groups={"testPackageGroup"}, dataProvider="pkgGroupData")
	public void createPkgGroup(String pkgGroupName, String repoId) {
		// generate repo list
		ArrayList<String> pkgList = new ArrayList<String>();
		for (List<Object> pkgInfo : getLocalPkgData()) {
			//ArrayList pkg = (ArrayList)pkgInfo.get(0);
			String pkgName = (String)pkgInfo.get(0);
			String tmpPath = (String)pkgInfo.get(2);
			pkgList.add(tmpPath + "/" + pkgName);
		}
		servertasks.createPkgGroup(pkgGroupName, repoId, pkgList);
	}

/*
	@Test (groups={"testPackageGroup"}, dataProvider="addPkgData", dependsOnMethods={"createPkgGroup"})
	public void addPkgToGroup(String pkgGroupName, String repoId, String pkgName) {
		servertasks.addToPkgGroup(pkgGroupName, repoId, pkgName);
	}
*/

	//@Test (groups={"testPackageGroup"}, dataProvider="installData", dependsOnMethods={"addPkgToGroup"})
	@Test (groups={"testPackageGroup"}, dataProvider="installData", dependsOnMethods={"createPkgGroup"})
	public void installPkgGroup(String pkgGroupName, String repoId, String consumerId, PulpTasks task) {
		String result = task.installPkgGroup(pkgGroupName, consumerId);
		//Assert.assertTrue(result.contains("installed on "+consumerId), "Verifying package group installation was successful.");
	}

	@Test (groups={"testPackageGroup"}, dataProvider="installData", dependsOnMethods={"installPkgGroup"})
	public void verifyInstallPkgGroup(String pkgGroupName, String repoId, String consumerId, PulpTasks task) {
		ArrayList<Hashtable<String, String>> filteredResult = pulpAbs.match(servertasks.pkgGroupInfo(pkgGroupName, repoId));
		String[] pkgs = decodePkg(filteredResult.get(0).get("pkgGroupDefaultPkg"));
		for (String pkgName : pkgs) {
			String result = task.packageSearch(pkgName);
			Assert.assertTrue(result.contains(pkgName), "Determining if rpm is correctly installed.");
			uninstallPkg(pkgName, task);
		}
	}

/*
	@Test (groups={"testPackageGroup"}, dataProvider="addPkgData", dependsOnMethods={"verifyInstallPkgGroup"}, alwaysRun=true)
	public void removePkgFromGroup(String pkgGroupName, String repoId, String pkgName) {
		servertasks.removePkgFromGroup(pkgGroupName, repoId, pkgName);
	}
*/

	@Test (groups={"testPackageGroup"}, dataProvider="pkgGroupData", dependsOnMethods={"verifyInstallPkgGroup"}, alwaysRun=true)
	public void removePkgGroup(String pkgGroupName, String repoId) {
		servertasks.deletePkgGroup(pkgGroupName, repoId, true);
	}

	@Test (groups={"testPackageGroup"}, dataProvider="pkgGroupData", dependsOnMethods={"removePkgGroup"}, alwaysRun=true)
	public void verifyPkgGroupDelete(String pkgGroupName, String repoId) {
		;
		ArrayList<Hashtable<String, String>> filteredResult = pulpAbs.match(servertasks.listPkgGroup(repoId, false));
		Assert.assertEquals(filteredResult.size(), 0,"Verifying pkg group deletion.");
	}

/*
	
	@Test (groups={"testPackageGroup"}, dataProvider="compsFileRepoData", dependsOnMethods={"verifyPkgGroupDelete"})
	public void importPkgGroupViaComps(String repoId, String fileName, String fileURL, String tmpDir) {
		servertasks.importPkgGroupViaComps(repoId, fileName, fileURL, tmpDir);
	}

	@Test (groups={"testPackageGroup"}, dataProvider="compsData", dependsOnMethods={"importPkgGroupViaComps"})
	public void verifyPkgGroupCreateViaComps(String repoId, String pkgGroupName, String pkgList) {
		ArrayList<Hashtable<String, String>> filteredResult = pulpAbs.match(servertasks.pkgGroupInfo(pkgGroupName, repoId));
		String[] pkgs = decodePkg(filteredResult.get(0).get("pkgGroupDefaultPkg"));
		for (String pkgName : pkgs) {
			System.out.println(pkgName);
			Assert.assertTrue(pkgList.contains(pkgName), "Determining if group list is correctly added.");
		}
	}

	@Test (groups={"testPackageGroup"}, dataProvider="exportCompsFileRepoData", dependsOnMethods={"verifyPkgGroupCreateViaComps"})
	public void exportPkgGroupViaComps(String repoId, String fileName, String tmpDir) {
		servertasks.exportPkgGroupViaComps(repoId, fileName, tmpDir);
	}
	
	@Test (groups={"testPackageGroup"}, dataProvider="exportCompsFileData", dependsOnMethods={"exportPkgGroupViaComps"})
	public void verifyExportPkgGroup(String fileName, String tmpDir) {
		servertasks.localFetchFile("http://qeblade20.rhq.lab.eng.bos.redhat.com/pub/test_comps.xml", "/tmp");
		servertasks.localFetchFile("http://" + serverHostname + "/" + fileName, "/tmp");

		ArrayList<Hashtable<String, String>> allGroups = servertasks.getAllGroupsFromCompXML("/tmp/test_comps.xml");
		for (Hashtable<String, String> group : allGroups) {
			// only check for ID and description for now
			try {
				boolean result = servertasks.doesCompGroupExist("/tmp/" + fileName, group.get("id"), group.get("description"));
				Assert.assertTrue(result, "Check if comps group id and description matches.");
			}
			catch (NullPointerException e) {
				e.printStackTrace();
			}
		}
	}
	
	// Negative Test - Make sure we can't remove imported(immutable) groups
	@Test (groups={"testPackageGroup"}, dataProvider="compsData", dependsOnMethods={"verifyPkgGroupCreateViaComps"}, alwaysRun=true)
	public void removeCompsPkgGroup(String repoId, String pkgGroupName, String pkgList) {
		String result = servertasks.deletePkgGroup(pkgGroupName, repoId, true); // FIXME: this is a bug on the CLI side, right now its $? is 0 
		Assert.assertTrue(result.contains("Unable"), "Making sure pulp cannot remove immutable groups.");
	}
*/

	@DataProvider(name="localRepoData") 
	public Object[][] localRepoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalRepoData());
	}
	public List<List<Object>> getLocalRepoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();

		ArrayList<String> repoOpts = new ArrayList<String>();
		repoOpts.add("--repo-id=package_group_test_repo");
		data.add(Arrays.asList(new Object[]{repoOpts}));

		return data;
	}

	@DataProvider(name="pkgGroupData")
	public Object[][] pkgGroupData() {
		return TestNGUtils.convertListOfListsTo2dArray(getPkgGroupData());
	}
	public List<List<Object>> getPkgGroupData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		data.add(Arrays.asList(new Object[]{"pulp_test_group", "package_group_test_repo"}));
		return data;
	}
	
	@DataProvider(name="addPkgData")
	public Object[][] addPkgData() {
		return TestNGUtils.convertListOfListsTo2dArray(getAddData());
	}
	public List<List<Object>> getAddData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		// (¬_¬) this is rather crude... manual O(n^2) implementation
		//       curse you java! where's my zip()
		for (List<Object> pkg : getLocalPkgData()) {
			for (List<Object> pkgGroup : getPkgGroupData()) {
				data.add(Arrays.asList(new Object[]{(String)pkgGroup.get(0), (String)pkgGroup.get(1), (String)pkg.get(3)}));
			}
		}
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

	@DataProvider(name="installData")
	public Object[][] installData() {
		return TestNGUtils.convertListOfListsTo2dArray(getInstallData());
	}
	public List<List<Object>> getInstallData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		// (¬_¬) this is rather crude... manual O(n^2) implementation
		//       curse you java! where's my zip()
		for (List<Object> consumer : getConsumerData()) {
			for (List<Object> pkgGroup : getPkgGroupData()) {
				data.add(Arrays.asList(new Object[]{(String)pkgGroup.get(0), (String)pkgGroup.get(1), (String)consumer.get(0), (PulpTasks)consumer.get(1)}));
			}
		}
		return data;
	}

	@DataProvider(name="localPkgData")
	public Object[][] localPkgData() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalPkgData());
	}
	public List<List<Object>> getLocalPkgData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		data.add(Arrays.asList(new Object[]{"patb-0.1-1.noarch.rpm", "http://qeblade20.rhq.lab.eng.bos.redhat.com/pub/patb-0.1-1.noarch.rpm", tmpRpmDir, "patb"}));
		data.add(Arrays.asList(new Object[]{"emoticons-0.1-1.noarch.rpm", "http://qeblade20.rhq.lab.eng.bos.redhat.com/pub/emoticons-0.1-1.noarch.rpm", tmpRpmDir, "emoticons"}));
		data.add(Arrays.asList(new Object[]{"origin-1.0-1.noarch.rpm", "http://qeblade20.rhq.lab.eng.bos.redhat.com/pub/updates/origin-1.0-1.noarch.rpm", tmpRpmDir, "origin"}));
		data.add(Arrays.asList(new Object[]{"parent-1.0-1.noarch.rpm", "http://qeblade20.rhq.lab.eng.bos.redhat.com/pub/updates/parent-1.0-1.noarch.rpm", tmpRpmDir, "parent"}));
		data.add(Arrays.asList(new Object[]{"feedless-1.0-1.noarch.rpm", "http://qeblade20.rhq.lab.eng.bos.redhat.com/pub/updates/feedless-1.0-1.noarch.rpm", tmpRpmDir, "feedless"}));
		return data;
	}

	@DataProvider(name="compsFileData")
	public Object[][] compsFileData() {
		return TestNGUtils.convertListOfListsTo2dArray(getCompsFileData());
	}
	public List<List<Object>> getCompsFileData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		data.add(Arrays.asList(new Object[]{"test_comps.xml", "http://qeblade20.rhq.lab.eng.bos.redhat.com/pub/test_comps.xml", tmpRpmDir}));
		return data;
	}
	
	@DataProvider(name="exportCompsFileData")
	public Object[][] exportCompsFileData() {
		return TestNGUtils.convertListOfListsTo2dArray(getExportCompsFileData());
	}
	public List<List<Object>> getExportCompsFileData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		data.add(Arrays.asList(new Object[]{"generated_comps.xml", "/var/www/html/"})); // the tmpDir really shouldn't be changed
																						// it's used to fetch the file later for comparison
		return data;
	}

	@DataProvider(name="compsFileRepoData")
	public Object[][] compsFileRepoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getCompsFileRepoData());
	}
	public List<List<Object>> getCompsFileRepoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(0)).replace("--repo-id=", "");
			for (List<Object> compsFile : getCompsFileData()) {
				data.add(Arrays.asList(new Object[]{repoId, compsFile.get(0), compsFile.get(1), compsFile.get(2)}));
			}
		}
		return data;
	}
	
	@DataProvider(name="exportCompsFileRepoData")
	public Object[][] exportCompsFileRepoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getExportCompsFileRepoData());
	}
	public List<List<Object>> getExportCompsFileRepoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(0)).replace("--repo-id=", "");
			for (List<Object> compsFile : getExportCompsFileData()) {
				data.add(Arrays.asList(new Object[]{repoId, compsFile.get(0), compsFile.get(1)}));
			}
		}
		return data;
	}

	@DataProvider(name="compsData")
	public Object[][] compsData() {
		return TestNGUtils.convertListOfListsTo2dArray(getCompsData());
	}
	public List<List<Object>> getCompsData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(0)).replace("--repo-id=", "");
			data.add(Arrays.asList(new Object[]{repoId, "Test_Comps_Group", "patb, emoticons"}));
			data.add(Arrays.asList(new Object[]{repoId, "Clones", "origin, parent, feedless"}));
		}
		return data;
	}
}
