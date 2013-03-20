package com.redhat.qe.pulp.cli.tests;

import com.redhat.qe.pulp.cli.base.PulpTestScript;
import com.redhat.qe.pulp.cli.tasks.PulpTasks;

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

// TODO: Sprinkle negative tests:
//		 - add/remove_package the same pkg

public class ContentTest extends PulpTestScript {
	private String auto_resources = System.getProperty("automation.resources.location");

	public ContentTest() {
		super();
	}

	@BeforeClass (groups={"testContent"}) 
	public void createTestRepo() {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			servertasks.createTestRepo(repoData);
		}
	}

	@Test (groups="testContent") 
	public void initialOrphanPkgCount() {
		String result = servertasks.getOrphanList();
		// If the following assert fails, that means pulp didn't not clean up
		// pkgs correctly when the associated repo got deleted.
		Assert.assertTrue(result.contains("No orphaned"), "Making sure there's no orphan packages prior to content testing.");
	}

	@Test (groups="testContent", dataProvider="localPkgData", dependsOnMethods={"initialOrphanPkgCount"}) 
	public void uploadTestPkgs(String rpmName, String rpmUrl, String tmpRpmDir, String pkgName, boolean inCSV) {
		servertasks.uploadContent("", rpmName, rpmUrl, tmpRpmDir, true);
	}

	@Test (groups="testContent", dataProvider="localContentData", dependsOnMethods={"initialOrphanPkgCount"}) 
	public void uploadTestContent(String fileName, String fileUrl, String tmpFileDir, String fileAlias, boolean inCSV) {
		servertasks.uploadContent("", fileName, fileUrl, tmpFileDir, true);
	}

	@Test (groups="testContent", dataProvider="localCSVData", dependsOnMethods={"initialOrphanPkgCount"}) 
	public void fetchTestCSV(String fileName, String fileUrl, String tmpFileDir) {
		servertasks.fetchFile(fileUrl + fileName, tmpFileDir);
	}

	@Test (groups="testContent", dependsOnMethods={"uploadTestPkgs", "uploadTestContent"}) 
	public void orphansCountPostUpload() {
		String result = servertasks.getOrphanList();
		Assert.assertFalse(result.contains("No orphaned"), "Making sure there are orphan packages/contents after upload.");
	}

	@Test (groups="testContent", dataProvider="localRepoPkgData", dependsOnMethods={"orphansCountPostUpload"})
	public void addPkgToRepo(String repoId, String pkgName) {
		servertasks.addPackage(repoId, pkgName, "");
	}

	@Test (groups="testContent", dataProvider="localRepoContentData", dependsOnMethods={"orphansCountPostUpload"})
	public void addContentToRepo(String repoId, String fileName) {
		servertasks.addContent(repoId, fileName, "");
	}

	@Test (groups="testContent", dataProvider="localRepoCSVData", dependsOnMethods={"orphansCountPostUpload"})
	public void addPkgToRepoViaCSV(String repoId, String csv) {
		if (csv.contains("package")) { // ugh, somewhat hardcoded
			servertasks.addPackage(repoId, "", csv);
		}
	}

	@Test (groups="testContent", dataProvider="localRepoCSVData", dependsOnMethods={"orphansCountPostUpload"})
	public void addContentToRepoViaCSV(String repoId, String csv) {
		if (csv.contains("content")) { // ugh, somewhat hardcoded
			servertasks.addContent(repoId, "", csv);
		}
	}

	@Test (groups="testContent", dataProvider="localRepoData", dependsOnMethods={"addPkgToRepo", "addPkgToRepoViaCSV"})
	public void verifyPkgAdd(ArrayList<String> repo) {
		String repoId = repo.get(0).replace("--id=", "");
		String contentResult = servertasks.getContentList(repoId);
		String repoResult = servertasks.getRepoPackageList(repoId);

		for (List<Object> pkg : getLocalPkgData()) {
			String pkgName = (String)pkg.get(0);
			Assert.assertTrue(contentResult.contains(pkgName), "Making sure content list reflects the added pkg: " + pkgName);
			Assert.assertTrue(repoResult.contains(pkgName), "Making sure repo content list reflects the added pkg: " + pkgName);
		}
	}

	@Test (groups="testContent", dataProvider="localRepoData", dependsOnMethods={"addContentToRepo", "addContentToRepoViaCSV"})
	public void verifyContentAdd(ArrayList<String> repo) {
		String repoId = repo.get(0).replace("--id=", "");
		String contentResult = servertasks.getContentList(repoId);
		String repoResult = servertasks.getRepoPackageList(repoId);

		for (List<Object> file : getLocalContentData()) {
			String fileName = (String)file.get(0);
			Assert.assertTrue(contentResult.contains(fileName), "Making sure content list reflects the added file: " + fileName);
			Assert.assertTrue(repoResult.contains(fileName), "Making sure repo content list reflects the added file: " + fileName);
		}
	}

	@Test (groups="testContent", dataProvider="localRepoPkgData", dependsOnMethods={"verifyPkgAdd"}) 
	public void deleteTestPkgs(String repoId, String pkgName) {
		servertasks.deletePackage(repoId, pkgName, "");
	}

	@Test (groups="testContent", dataProvider="localRepoContentData", dependsOnMethods={"verifyContentAdd"}) 
	public void deleteTestContent(String repoId, String fileName) {
		servertasks.deleteContent(repoId, fileName, "");
	}

	@Test (groups="testContent", dataProvider="localRepoCSVData", dependsOnMethods={"verifyPkgAdd"}) 
	public void deleteTestPkgViaCSV(String repoId, String csv) {
		if (csv.contains("package")) { // ugh, somewhat hardcoded
			servertasks.deletePackage(repoId, "", csv);
		}
	}

	@Test (groups="testContent", dataProvider="localRepoCSVData", dependsOnMethods={"verifyContentAdd"}) 
	public void deleteTestContentViaCSV(String repoId, String csv) {
		if (csv.contains("content")) { // ugh, somewhat hardcoded
			servertasks.deleteContent(repoId, "", csv);
		}
	}

	// Problem: It's an issue when the repo exists, thus prereq cannot run, and as a result, alwaysRun doesn't really do anything
	@Test (groups={"testContent"}, alwaysRun=true, dependsOnMethods={"deleteTestPkgs", "deleteTestContent", "deleteTestPkgViaCSV", "deleteTestContentViaCSV"})
	public void deleteTestRepo() {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(0)).replace("--id=", "");
			servertasks.deleteTestRepo(repoId);
		}
	}

	@Test (groups={"testContent"}, alwaysRun=true, dependsOnMethods={"deleteTestRepo"})
	public void burnOrphanage() {
		// god i hope i don't go onto the FBI watch list for this
		for (List<Object> csv : getLocalCSVData()) {
			servertasks.deleteOrphanedContent("", (String)csv.get(2) + "/" + (String)csv.get(0));
		}
	}

	@Test (groups={"testContent"}, alwaysRun=true, dependsOnMethods={"burnOrphanage"})
	public void wipeSurvivors() {
		String result = servertasks.getOrphanList();
		for (String pkgSHAPair : result.split("\n")) {
			String pkgName = pkgSHAPair.split(",")[0].trim();
			servertasks.deleteOrphanedContent(pkgName, "");
		}
	}

	@Test (groups={"testContent"}, alwaysRun=true, dependsOnMethods={"wipeSurvivors"})
	public void verifyDeleteTestRepo() {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = (String)repoData.get(0);
			ArrayList<Hashtable<String, ArrayList<String>>> filteredResult = this.match(servertasks.listRepo(true), "   ");
			Hashtable<String, ArrayList<String>> result = pulpAbs.findRepo(filteredResult, repoId);
			Assert.assertNull(result.get("repoId"), "comparing repoId to null.");
		}
	}

	// verify no leftover rpms on FS and mongo?

	@DataProvider(name="localRepoData")
	public Object[][] localRepoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalRepoData());
	}
	public List<List<Object>> getLocalRepoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();

		ArrayList<String> repoOpts = new ArrayList<String>();
		repoOpts.add("--id=content_test_repo");
		data.add(Arrays.asList(new Object[]{repoOpts}));

		return data;
	}

	@DataProvider(name="localPkgData")
	public Object[][] localPkgData() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalPkgData());
	}
	public List<List<Object>> getLocalPkgData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		data.add(Arrays.asList(new Object[]{"parent-1.0-1.noarch.rpm", auto_resources + "/updates/parent-1.0-1.noarch.rpm", tmpRpmDir, "parent-1.0-1.noarch.rpm", false}));
		data.add(Arrays.asList(new Object[]{"origin-1.0-1.noarch.rpm", auto_resources + "/updates/origin-1.0-1.noarch.rpm", tmpRpmDir, "origin-1.0-1.noarch.rpm", false}));
		data.add(Arrays.asList(new Object[]{"feedless-1.0-1.noarch.rpm", auto_resources + "/updates/feedless-1.0-1.noarch.rpm", tmpRpmDir, "feedless-1.0-1.noarch.rpm", false}));
		data.add(Arrays.asList(new Object[]{"patb-0.1-1.noarch.rpm", auto_resources + "/patb-0.1-1.noarch.rpm", tmpRpmDir, "patb-0.1-1.noarch.rpm", true}));
		data.add(Arrays.asList(new Object[]{"emoticons-0.1-1.noarch.rpm", auto_resources + "/emoticons-0.1-1.noarch.rpm", tmpRpmDir, "emoticons-0.1-1.noarch.rpm", true}));
		data.add(Arrays.asList(new Object[]{"patb-0.1-2.noarch.rpm", auto_resources + "/updates/patb-0.1-2.noarch.rpm", tmpRpmDir, "patb-0.1-2.noarch.rpm", true}));
		data.add(Arrays.asList(new Object[]{"emoticons-0.1-2.noarch.rpm", auto_resources + "/updates/emoticons-0.1-2.noarch.rpm", tmpRpmDir, "emoticons-0.1-2.noarch.rpm", true}));
		return data;
	}

	@DataProvider(name="localContentData")
	public Object[][] localContentData() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalContentData());
	}
	public List<List<Object>> getLocalContentData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		data.add(Arrays.asList(new Object[]{"dummy_content.csv", auto_resources + "/dummy_content.csv", tmpRpmDir, "dummy_content", false}));
		data.add(Arrays.asList(new Object[]{"test_content-0.1-0.csv", auto_resources + "/test_content-0.1-0.csv", tmpRpmDir, "test_content", true}));
		data.add(Arrays.asList(new Object[]{"test_content-0.1-1.csv", auto_resources + "/test_content-0.1-1.csv", tmpRpmDir, "test_content", true}));
		return data;
	}

	@DataProvider(name="localCSVData")
	public Object[][] localCSVData() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalCSVData());
	}
	public List<List<Object>> getLocalCSVData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		data.add(Arrays.asList(new Object[]{"package.csv", auto_resources, tmpRpmDir}));
		data.add(Arrays.asList(new Object[]{"content.csv", auto_resources, tmpRpmDir}));
		// Mix n Match?
		return data;
	}
	
	@DataProvider(name="localRepoPkgData")
	public Object[][] localRepoPkgData() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalRepoPkgData());
	}
	public List<List<Object>> getLocalRepoPkgData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		// (¬_¬) this is rather crude... manual O(n^2) implementation
		//       curse you java! where's my zip()
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(0)).replace("--id=", "");
			for (List<Object> pkg : getLocalPkgData()) {
				if (!(Boolean)pkg.get(4)) {
					data.add(Arrays.asList(new Object[]{repoId, (String)pkg.get(3)}));
				}
			}
		}
		return data;
	}

	@DataProvider(name="localRepoContentData")
	public Object[][] localRepoContentData() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalRepoContentData());
	}
	public List<List<Object>> getLocalRepoContentData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		// (¬_¬) this is rather crude... manual O(n^2) implementation
		//       curse you java! where's my zip()
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(0)).replace("--id=", "");
			for (List<Object> file : getLocalContentData()) {
				if (!(Boolean)file.get(4)) {
					data.add(Arrays.asList(new Object[]{repoId, (String)file.get(3)}));
				}
			}
		}
		return data;
	}

	@DataProvider(name="localRepoCSVData")
	public Object[][] localRepoCSVData() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalRepoCSVData());
	}
	public List<List<Object>> getLocalRepoCSVData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		// (¬_¬) this is rather crude... manual O(n^2) implementation
		//       curse you java! where's my zip()
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(0)).replace("--id=", "");
			for (List<Object> csv : getLocalCSVData()) {
				data.add(Arrays.asList(new Object[]{repoId, (String)csv.get(2) + "/" + (String)csv.get(0)}));
			}
		}
		return data;
	}
}
