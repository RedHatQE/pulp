package com.redhat.qe.pulp.cli.tests;

import java.util.Hashtable;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.Iterator;
import java.util.Enumeration;

import org.testng.annotations.Test;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import com.redhat.qe.auto.testng.TestNGUtils;
import com.redhat.qe.Assert;
import org.testng.annotations.DataProvider;

import com.redhat.qe.pulp.cli.tasks.PulpTasks;
import com.redhat.qe.pulp.cli.base.PulpTestScript;

import com.redhat.qe.pulp.abstraction.PulpAbstraction;
import com.redhat.qe.tools.SSHCommandRunner;

public class RepoTest extends PulpTestScript {
	public RepoTest() {
		super();
		Hashtable<String, String> rules = new Hashtable<String, String>();
		
		rules.put("repoStatusName", "^Repository:[\\s]+([a-zA-Z0-9\\_]+)[\\s]*$");
		rules.put("repoStatusPkgCount", "^Number of Packages:[\\s]+([0-9]+)[\\s]*$");
		rules.put("repoStatusLastSync", "^Last Sync:[\\s]+([a-zA-Z0-9\\_]+)[\\s]*$");
		rules.put("repoStatusSyncing", "^Currently syncing:[\\s]+([a-zA-Z0-9\\_\\(\\)\\%\\s\\.]+)[\\s]*$");

		rules.put("repoScheduleHeader", "^[\\s]*Available Repository Schedules[\\s]*$");
		rules.put("repoScheduleLabel","^Label[\\s]+([a-zA-Z0-9\\_]+)[\\s]*$");
		rules.put("repoSchedule", "^Schedule[\\s]+([0-9\\*\\-\\s\\,]+)[\\s]*$");
		
		pulpAbs.appendRegexCriterion(rules);
	}

	@Test (groups={"testRepo"}, dataProvider="localRepoData")
	public void createTestRepo(ArrayList<String> fields, boolean cancelable) {
		servertasks.createTestRepo(fields);
	}

	@Test (groups={"testRepo"}, dataProvider="localRepoData", dependsOnMethods="createTestRepo")
	public void verifyCancelSync(ArrayList<Object> fields, boolean cancelable) {
		if (cancelable) {
			String repoId = ((String)fields.get(0)).replace("--id=", "");
			servertasks.syncTestRepo(repoId, false);
			// verify that it's syncing
			//
			// i'll give it 10 5s grace periods to change sync status from "unknown" to an actual percentage
			boolean attempted_cancel_sync = false;
			for (int i=0; i<10; i++) {
				ArrayList<Hashtable<String, ArrayList<String>>> filteredResult = this.match(servertasks.listRepoStatus(repoId), ":");
				if (pulpAbs.findRepoStatus(filteredResult, repoId).containsKey("Currently syncing")) {
					servertasks.cancelSyncTestRepo(repoId);
					attempted_cancel_sync = true;
					break;
				}
				try {
					Thread.currentThread();
					Thread.sleep(10000);
				}
				catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			Assert.assertTrue(attempted_cancel_sync, "Attempt to cancel sync");
		}
	}

	@Test (groups={"testRepo"}, dataProvider="localRepoData", dependsOnMethods="verifyCancelSync", alwaysRun=true)
	public void cleanupPartialSyncRepo(ArrayList<Object> fields, boolean cancelable) {
		if (cancelable) {
			String repoId = ((String)fields.get(0)).replace("--id=", "");
			servertasks.deleteTestRepo(repoId);
		}
	}

	// TODO: verify wipe
	@Test (groups={"testRepo"}, dataProvider="localRepoData", dependsOnMethods="cleanupPartialSyncRepo")
	public void verifyCleanup(ArrayList<Object> fields, boolean cancelable) {
		if (cancelable) {
			String repoId = ((String)fields.get(0)).replace("--id=", "");
		}
	}

	@Test (groups={"testRepo"}, dataProvider="localRepoData", dependsOnMethods="verifyCleanup")
	public void syncLocalRepo(ArrayList<Object> fields, boolean cancelable) {
		String repoFeed = ((String)fields.get(1)).replace("--feed=", "");
		if (!cancelable && !repoFeed.equals("")) {
			String repoId = ((String)fields.get(0)).replace("--id=", "");
			servertasks.syncTestRepo(repoId, true);
		}
	}

	@Test (groups={"testRepo"}, dataProvider="localRepoData", dependsOnMethods="syncLocalRepo")
	public void verifySync(ArrayList<Object> fields, boolean cancelable) {
		if (!cancelable) {
			String repoId = ((String)fields.get(0)).replace("--id=", "");
			String repoPath = ((String)fields.get(2)).replace("--relativepath=", "");

			String folderPath = "";
			if (repoPath.equals("")) {
				// TODO: The update part of the path is hardcoded...I'm taking advantage of the
				// fact that I know all my test repos are synced to the same source.
				folderPath = "/var/lib/pulp/repos/pub/updates";
			}
			else {
				folderPath = "/var/lib/pulp/repos" + repoPath;
			}	

			ArrayList<Hashtable<String, ArrayList<String>>> filteredResult = this.match(servertasks.listRepoStatus(repoId), ":");
			int statusResult = pulpAbs.getRepoPkgCountFromStatus(filteredResult, repoId, "Repository");
			//String[] expectedResult = servertasks.getRepoPackageList(repoId).split("packages in "+repoId+":\n")[1].split("\n");
			String[] intermediateResult = servertasks.getRepoPackageList(repoId).split("\n");
			// filter the output so it only has the pkg 
			ArrayList<String> expectedResult = new ArrayList<String>();
			for (String s : intermediateResult) {
				if (s.contains("rpm")) {
					expectedResult.add(s);
				}
			}
			Assert.assertEquals(statusResult, expectedResult.size(), "Determing if the reported number of pkg available is equivalent.");

			for (String rpmName : expectedResult) {
				String fullPath = folderPath + "/" + rpmName.trim();
				Assert.assertTrue(servertasks.doesLinkExist(fullPath), "Verifying " + rpmName + " is on FS.");
			}
		}
	}

	@Test (groups={"testRepo"}, dataProvider="localRepoData", dependsOnMethods="verifySync")
	public void uploadPkg(ArrayList<Object> fields, boolean cancelable) {
		String repoFeed = ((String)fields.get(1)).replace("--feed=", "");
		if (repoFeed.equals("")) {
			String repoId = ((String)fields.get(0)).replace("--id=", "");
			for (List<Object> pkg : getPkgData()) {
				String rpmName = (String)pkg.get(0);
				String rpmURL = (String)pkg.get(1);
				String rpmDir = (String)pkg.get(2);
				// Temporary workaround for content bz 752098
				this.getMetadataStatus(repoId);
				servertasks.uploadContent(repoId, rpmName, rpmURL, rpmDir, true);

				verifyUpload(repoId, rpmName);
				verifyRPMInLocation(repoId, rpmName);
			}
		}
	}
/*
	@Test (groups={"testRepo"}, dataProvider="localRepoData", dependsOnMethods="uploadPkg")
	public void uploadFile(ArrayList<Object> fields, boolean cancelable) {
		String repoFeed = ((String)fields.get(1)).replace("--feed=", "");
		if (repoFeed.equals("")) {
			String repoId = ((String)fields.get(0)).replace("--id=", "");
		}
	}
*/

	@Test (groups={"testRepo"}, dataProvider="modRepoData")
	public void createModRepo(ArrayList<String> fields) {
		servertasks.createTestRepo(fields);
	}

/*
	@Test (groups={"testRepo"}, dataProvider="modRepoData", dependsOnMethods="createModRepo")
	public void updateRepo(ArrayList<Object> fields) {
		// TODO: change everything including feed url
		String repoId = ((String)fields.get(0)).replace("--id=", "");
		ArrayList<String> repoOpts = new ArrayList<String>();
		repoOpts.add("--id="+repoId);
		repoOpts.add("--name=mod_repo_name");
		repoOpts.add("--arch=noarch");
		repoOpts.add("--feed=" + System.getProperty("automation.resources.location") + "/updates");
		//repoOpts.add("--schedule=\"0,30 * * * *\"");
		//repoOpts.add("--cacert=");
		//repoOpts.add("--relativepath=");
		//repoOpts.add("--groupid=");
		servertasks.updateTestRepo(repoOpts, true);
	}

	@Test (groups={"testRepo"}, dataProvider="modRepoData", dependsOnMethods={"updateRepo"})
	public void verifyRepoUpdate(ArrayList<Object> fields) {
		String repoId = ((String)fields.get(0)).replace("--id=", "");
		ArrayList<Hashtable<String, String>> filteredResult = pulpAbs.match(servertasks.listRepo(true));
		Hashtable<String, String> result = pulpAbs.findRepo(filteredResult, repoId);
		Assert.assertEquals(result.get("repoId"), repoId, "comparing repoId to " + repoId);
		Assert.assertEquals(result.get("repoName"), "mod_repo_name", "comparing repoName to " + repoId);
		Assert.assertEquals(result.get("repoArch"), "noarch", "comparing repoArch to " + repoArch);
		Assert.assertEquals(result.get("repoLocation"), System.getProperty("automation.resources.location") + "/updates", "comparing repoLocation to " + repoLocation);
	}
*/

	//@Test (groups={"testRepo"}, dataProvider="modRepoData", dependsOnMethods={"verifyRepoUpdate"})
	@Test (groups={"testRepo"}, dataProvider="modRepoData", dependsOnMethods={"createModRepo"})
	public void syncModRepo(ArrayList<Object> fields) {
		String repoId = ((String)fields.get(0)).replace("--id=", "");
		//servertasks.syncTestRepo(repoId, true);
	}

	// Negative
	/*
	@Test (groups={"testRepo"}, dataProvider="modRepoData", dependsOnMethods="syncModRepo")
	public void updateSyncedRepo(ArrayList<Object> fields) {
		// update feed and assert for failure but not traceback
		String repoId = ((String)fields.get(0)).replace("--id=", "");
		ArrayList<String> repoOpts = new ArrayList<String>();
		repoOpts.add("--id="+repoId);
		repoOpts.add("--feed=" + System.getProperty("automation.resources.location") + "/updates");
		servertasks.updateTestRepo(repoOpts, false);
		// verify that the feed didn't change
	}
	*/

	@Test (groups={"testRepo"}, dataProvider="chunkyUploadData")
	public void uploadAndVerifyChunkData(String fileName, String fileURL, String tmpDir, String chunkSize) {
		/* *Note* 
		 * Mechanically, I'm not sure if spawning upload in background is equivalent/worse/better than setting up another
		 * PulpTasks obj., have servertasks run upload & while(sshCommandRunner.wait()) {
		 * obj.getSegments();
		 * }
		 * 
		 * The main concern is that the upload task is actually not complete until the CLI call returns. 
		 * The test can verify the chunk result and try to cleanup prior the the initial CLI call returns. 
		 */
		
		try {
			SSHCommandRunner subRunner = new SSHCommandRunner(serverHostname, sshUser, sshPassphrase, sshKeyPrivate, sshKeyPassphrase, null);	
			String actualChunkSize = servertasks.uploadChunkedContent(fileName, fileURL, tmpDir, chunkSize, subRunner);
			Assert.assertEquals(chunkSize, actualChunkSize, "Comparing actual chunk size w/ passed in chunk size.");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
/*
	@AfterClass(groups={"testRepo"}, alwaysRun=true)
	public void waitForUploadFinish() {
		// just in case
		try {
			while (true) {
				if (servertasks.getSegments().equals("")) {
					break;
				}
				Thread.currentThread();
				Thread.sleep(5000);
			}
			// apparently, there's a time period where chunks disappear from dir and the file shows up on content list
			Thread.currentThread();
			Thread.sleep(10000);
		}
		catch (Exception e) {
		}
	}
*/
	//@AfterClass(groups={"testRepo"}, alwaysRun=true, dependsOnMethods="waitForUploadFinish")
	@AfterClass(groups={"testRepo"}, alwaysRun=true)
	public void deleteLocalRepo() {
		String result = "";
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(0)).replace("--id=", "");
			servertasks.deleteTestRepo(repoId);
		}
		for (List<Object> repo : getModRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(0)).replace("--id=", "");
			servertasks.deleteTestRepo(repoId);
		}
	}

	@AfterClass (groups={"testRepo"}, alwaysRun=true, dependsOnMethods={"deleteLocalRepo"})
	public void verifyDeleteTestRepo() {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = (String)repoData.get(0);
			ArrayList<Hashtable<String, ArrayList<String>>> filteredResult = this.match(servertasks.listRepo(true), ":");
			Hashtable<String, ArrayList<String>> result = pulpAbs.findRepo(filteredResult, repoId);
		}
	}

	//@AfterClass(groups={"testRepo"}, alwaysRun=true, dependsOnMethods="waitForUploadFinish") 
	@AfterClass(groups={"testRepo"}, alwaysRun=true)
	public void deleteChunkUploadData() {
		for (List<Object> file : getCampbellChunkyIttyBittyPieces()) {
			servertasks.deleteOrphanedContent((String)file.get(0), "");
		}
	}

	// Utility
	// ###########################################################################
	public void verifyUpload(String repoId, String rpmFile) {
		String[] expectedResult = servertasks.getRepoPackageList(repoId).split("\n");
		ArrayList<String> rpmsFound = new ArrayList<String>();
		for (String s : expectedResult) {
			rpmsFound.add(s.trim());
		}

		Assert.assertTrue(rpmsFound.contains(rpmFile), "Verifying " + rpmFile + " is in the repo."); }
	
	public void verifyRPMInLocation(String relativeRepoPath, String searchFile) {
		String fullPath = "/var/lib/pulp/repos/" + relativeRepoPath + "/" + searchFile;
		Assert.assertTrue(servertasks.doesLinkExist(fullPath), "Verifying " + searchFile + " is on FS.");
	}
	// ###########################################################################
	@DataProvider(name = "localRepoData")
	public Object[][] localRepoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalRepoData());
	}
	public List<List<Object>> getLocalRepoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();

		ArrayList<String> repoOpts = new ArrayList<String>();
		repoOpts.add("--id=faux_test_repo");
		repoOpts.add("--feed=http://download.devel.redhat.com/released/F-13/GOLD/Fedora/x86_64/os");
		repoOpts.add("");
		data.add(Arrays.asList(new Object[]{repoOpts, true}));

		repoOpts = new ArrayList<String>();
		repoOpts.add("--id=relative_test_repo");
		repoOpts.add("--feed=" + System.getProperty("automation.resources.location") + "/updates/");
		repoOpts.add("--relativepath=/pulp_relative_path/");
		data.add(Arrays.asList(new Object[]{repoOpts, false}));

		repoOpts = new ArrayList<String>();
		repoOpts.add("--id=upload_test_repo");
		repoOpts.add(""); // don't remove this as we're using this field to determine whether we're syncing the repo or not
		repoOpts.add(""); // ^^^
		data.add(Arrays.asList(new Object[]{repoOpts, false}));

		return data;
	}

	@DataProvider(name = "modRepoData")
	public Object[][] modRepoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getModRepoData());
	}
	public List<List<Object>> getModRepoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();

		ArrayList<String> repoOpts = new ArrayList<String>();
		repoOpts.add("--id=mod_test_repo");
		repoOpts.add("--name=mod_test_repo");
		repoOpts.add("--feed=http://download.devel.redhat.com/released/F-14/GOLD/Fedora/x86_64/os");
		data.add(Arrays.asList(new Object[]{repoOpts}));

		return data;
	}

	@DataProvider(name = "chunkyUploadData")
	public Object[][] chunkyUploadData() {
		return TestNGUtils.convertListOfListsTo2dArray(getCampbellChunkyIttyBittyPieces());
	}
	public List<List<Object>> getCampbellChunkyIttyBittyPieces() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();

		data.add(Arrays.asList(new Object[]{"Fedora-14-x86_64-netinst.iso", System.getProperty("automation.resources.location") + "/Fedora-14-x86_64-netinst.iso", tmpRpmDir, "20971520"}));

		return data;
	}
}
