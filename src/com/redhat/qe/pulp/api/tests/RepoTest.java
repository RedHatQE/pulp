package com.redhat.qe.pulp.api.tests;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;

import com.redhat.qe.pulp.api.base.PulpTestScript;

import com.redhat.qe.Assert;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;
import com.redhat.qe.auto.testng.TestNGUtils;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

// TODO: Re-examine dependency and test run paths. 
//       Can Create -> Use -> Delete cycle be no-linear w/ the different
//		 type of DataProviders?

public class RepoTest extends PulpTestScript{
	private final String functionality;
	public RepoTest() {
		super();
		functionality = "repositories";
	}

	@Test (groups={"testRepo"})
	public void listTestRepo() {
		String resp = servertasks.executeGET(functionality, "", 200);
		try {
			JSONArray repos = JSONifyArray(resp);	
			Assert.assertEquals(repos.length(), 0, "Making sure the system is clean");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Test (groups={"testRepo"}, dataProvider="localRepoData", dependsOnMethods={"listTestRepo"})
	public void createTestRepo(Hashtable<String, String> fields) {
		try {
			JSONObject requestBody = new JSONObject(); 
			for (String key : fields.keySet()) {
				requestBody.put(key, fields.get(key)); 
			}
			String resp = servertasks.executePOST(functionality, "", requestBody.toString(), 201);
		}
		catch (JSONException jsone) {
			jsone.printStackTrace();
		}
	}

	@Test (groups={"testRepo"}, dataProvider="bigRepoData", dependsOnMethods={"listTestRepo"})
	public void createBigRepo(Hashtable<String, String> fields) {
		try {
			JSONObject requestBody = new JSONObject(); 
			for (String key : fields.keySet()) {
				requestBody.put(key, fields.get(key)); 
			}
			String resp = servertasks.executePOST(functionality, "", requestBody.toString(), 201);
		}
		catch (JSONException jsone) {
			jsone.printStackTrace();
		}
	}

	@Test (groups={"testRepo"}, dataProvider="botchedRepoData", dependsOnMethods={"listTestRepo"})
	public void createBotchedRepo(Hashtable<String, String> fields) {
		try {
			JSONObject requestBody = new JSONObject(); 
			for (String key : fields.keySet()) {
				requestBody.put(key, fields.get(key)); 
			}
			String resp = servertasks.executePOST(functionality, "", requestBody.toString(), 201);
		}
		catch (JSONException jsone) {
			jsone.printStackTrace();
		}
	}

	@Test (groups={"testRepo"}, dataProvider="multiSyncRepoData", dependsOnMethods={"listTestRepo"})
	public void createMultiSyncRepo(Hashtable<String, String> fields) {
		try {
			JSONObject requestBody = new JSONObject(); 
			for (String key : fields.keySet()) {
				requestBody.put(key, fields.get(key)); 
			}
			String resp = servertasks.executePOST(functionality, "", requestBody.toString(), 201);
		}
		catch (JSONException jsone) {
			jsone.printStackTrace();
		}
	}

	@Test (groups={"testRepo"}, dataProvider="localRepoData", dependsOnMethods={"createTestRepo", "createBotchedRepo", "createMultiSyncRepo"})
	public void verifyCreateTestRepo(Hashtable<String, String> fields) {
		String resp = servertasks.executeGET(functionality, "", 200);
		Assert.assertNotNull(resp);
		boolean match = false;
		try {
			JSONArray repos = JSONifyArray(resp);	
			for (int i=0; i < repos.length(); i++) {
				JSONObject obj = new JSONObject(repos.get(i).toString());
				if (fields.get("id").equals(obj.get("id"))) {
					match = true;
					break;
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		Assert.assertTrue(match, "Verifying if test repo " + fields.get("id") + " have been created in the system.");
	}

	// hmm should we have another verify w/ /pulp/api/repositories/id ?
	
	@Test (groups={"testRepo"}, dataProvider="localRepoData", dependsOnMethods={"verifyCreateTestRepo"})
	public void syncTestRepo(Hashtable<String, String> fields) {
		try {
			String taskId = executeRepoSync(fields.get("id"));

			// Make sure it's done and still not in the middle of it before checking
			while (true) {
				String resp = servertasks.executeGET(functionality, fields.get("id") + "/sync/" + taskId, 200);
				JSONObject statusObj = JSONifyString(resp);

				if (!statusObj.get("state").equals("running")) { // ok it's done
					JSONObject progressObj = (JSONObject)statusObj.get("progress");
					JSONObject detailObj = (JSONObject)progressObj.get("details");
					JSONObject rpm = (JSONObject)detailObj.get("rpm");
				
					Assert.assertEquals(statusObj.get("state"), "finished", "Making sure the sync state is set to finished.");
					Assert.assertEquals(rpm.get("num_error"), 0, "Making sure there was no failure during sync.");
					Assert.assertNotSame(rpm.get("num_success"), 0, "Making sure #pkg successfully synced > 0.");
					Assert.assertTrue(statusObj.isNull("traceback"), "Making sure there was no traceback.");
					break;
				}
			}
	
		}
		catch (JSONException jsone) {
			jsone.printStackTrace();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Test (groups={"testRepo"}, dataProvider="localRepoData", dependsOnMethods={"syncTestRepo"})
	public void verifySyncTestRepo(Hashtable<String, String> fields) {
		try {
				String resp = servertasks.executeGET(functionality, fields.get("id") + "/packages", 200);
				JSONArray pkgList = JSONifyArray(resp);	
				Assert.assertTrue((pkgList.length() > 0), "Making sure there are more than one pkg synced that pulp knows about.");
		}
		catch (JSONException jsone) {
			jsone.printStackTrace();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		// Physical file/rpm verification 

		// NOTE: 2 pieces of hardcoded-ness
		// 1) I'm assuming that the default pulp repo setting is public
		// 2) I'm taking advantage of the fact that the repo I sync is a known source, hence I know
		//    what the repo folder name is (in this case, it's "updates")
		String folderPath = "/var/lib/pulp/repos/pub/updates";
		ArrayList<String> rpmList = servertasks.findAllTestRpms(folderPath);
		Assert.assertTrue((rpmList.size() > 0), "Making sure the softlink for rpms in this repo is setup.");
	}

	@Test (groups={"testRepo"}, dataProvider="localRepoData", dependsOnMethods={"verifySyncTestRepo"})
	public void getSyncHistory(Hashtable<String, String> fields) {
		try {
			String resp = servertasks.executeGET(functionality, fields.get("id") + "/sync/", 200);
			JSONArray historyList = JSONifyArray(resp);	
			// TODO: Figure out why sync history persist after repo deletion
		}
		catch (JSONException jsone) {
			jsone.printStackTrace();	
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Test (groups={"testRepo"}, dataProvider="bigRepoData", dependsOnMethods={"getSyncHistory"})
	public void cancelSync(Hashtable<String, String> fields) {
		// Note:
		// My concern w/ the way this test is layed out is that if automation is ran on a slower 
		// machine/slave, is there a way to for the sync to finish before it reaches the cancel code?
		// Even w/ a fairly large repo like RHEL?
		// Implication of failure 
		//		- servertasks.executeDELETE() will fail since the return code will be 204 (No Content) 
		//		  instead of 202 (Accepted)
		try {
			executeRepoSync(fields.get("id"));
			String resp = cancelSync(fields.get("id"));
			Assert.assertNotNull(resp);
		}
		catch (JSONException jsone) {
			jsone.printStackTrace();
		}
	}

	// Negative Tests
	// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	@Test (groups={"testRepo"}, dataProvider="localRepoData", dependsOnMethods={"cancelSync"})
	public void cancelFinishedSync(Hashtable<String, String> fields) {
		try {
			String taskId = executeRepoSync(fields.get("id"));

			// Make sure it's done and still not in the middle of it before checking
			while (true) {
				String resp = servertasks.executeGET(functionality, fields.get("id") + "/sync/" + taskId, 200);
				JSONObject statusObj = JSONifyString(resp);
				
				if (!statusObj.get("state").equals("running")) { // ok it's done
					// execute cancel w/ expected 204 (No Content) return
					resp = servertasks.executeDELETE(functionality, fields.get("id") + "/sync/" + taskId, 204);
					break;
				}
			}
	
		}
		catch (JSONException jsone) {
			jsone.printStackTrace();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// Test Note: Check how "secure" the repo lock is during sync. 
	@Test (groups={"testRepo"}, dependsOnMethods={"cancelFinishedSync"})
	public void syncSpam() {
		List repoList = (List)getBigRepoData();
		List item = (List)repoList.get(0);
		Hashtable fields = (Hashtable)item.get(0);
		String id = (String)fields.get("id");
		JSONObject statusObj = null;
		ArrayList<String> taskIdList = new ArrayList<String>();
		try {
			String taskId = executeRepoSync(id);
			taskIdList.add(taskId);

			// Spam for 100x
			for (int i=0; i<100; i++) { // hmm what happens if sync finishes before the spam?
				System.out.println(">>> Now serving " + i + ". <<<");

				boolean active = false;

				// Check for active syncs
				String historyResp = servertasks.executeGET(functionality, fields.get("id") + "/sync/", 200);
				JSONArray historyList = JSONifyArray(historyResp);	
				for (int j=0; j < historyList.length(); j++) {
					JSONObject obj = new JSONObject(historyList.get(j).toString());
					JSONObject sub_obj = new JSONObject(obj.get("progress").toString());
					if (!sub_obj.get("status").equals("FINISHED")) {
						active = true;
					}
				}

				if (active) { 
					JSONObject requestBody = new JSONObject(); 
					String resp = servertasks.executePOST(functionality, id + "/sync/", requestBody.toString(), 409);
					Assert.assertTrue(resp.toLowerCase().contains("sync already in process"), "Making sure the response contains why sync fails.");
				}
				else {
					taskId = executeRepoSync(id);
					taskIdList.add(taskId);
				}
			}
		}
		catch (JSONException jsone) {
			jsone.printStackTrace();
		}
		finally {
				for (String taskId : taskIdList) {
/*
					String resp = servertasks.executeGET(functionality, id + "/sync/" + taskId, 200);
					statusObj = JSONifyString(resp);
					if (statusObj.get("state").equals("running")) { // ok it's in the middle of running
						servertasks.executeDELETE(functionality, fields.get("id") + "/sync/" + taskId, 202);
					}
*/
					cancelSync(taskId);
				}
		}
	}

	// Note: Is this test really necessary considering we already have numerous 
	//       while(true) status call in the test cases above. 
	@Test (groups={"testRepo"}, dependsOnMethods={"cancelFinishedSync"})
	public void syncStatusSpam() {
		List repoList = (List)getMultiSyncRepoData();
		List item = (List)repoList.get(0);
		Hashtable fields = (Hashtable)item.get(0);
		String id = (String)fields.get("id");
		JSONObject statusObj = null;
		try {
			String taskId = executeRepoSync(id);
			Assert.assertNotNull(taskId, "Making sure we got a sync id back for each spam item.");
			// Spam for 100x
			for (int i=0; i<100; i++) {
				System.out.println(">>> Now checking " + i + ". <<<");
				String resp = servertasks.executeGET(functionality, id + "/sync/" + taskId, 200);
				statusObj = JSONifyString(resp);
				Assert.assertTrue((statusObj.isNull("traceback") && statusObj.isNull("exception")), "Making sure sync didn't result in traceback nor exception.");
			}
		}
		catch (AssertionError ae) {
			System.out.println(statusObj);
			throw ae;
		}
		catch (JSONException jsone) {
			jsone.printStackTrace();
		}
		finally {
/*
				String resp = servertasks.executeGET(functionality, id + "/sync/" + taskId, 200);
				statusObj = JSONifyString(resp);
				if (statusObj.get("state").equals("running")) { // ok it's in the middle of running
					servertasks.executeDELETE(functionality, fields.get("id") + "/sync/" + taskId, 202);
				}
*/
				cancelSync(id);
		}
	}

	@Test (groups={"testRepo"}, dataProvider="botchedRepoData", dependsOnMethods={"cancelFinishedSync"})
	public void syncBotchedRepo(Hashtable<String, String> fields) {
		try {
			String taskId = executeRepoSync(fields.get("id"), 202);
			Assert.assertNotNull(taskId, "Making sure we got a sync id back for each spam item.");
			String resp = servertasks.executeGET(functionality, fields.get("id") + "/sync/" + taskId, 200);
			JSONObject statusObj = JSONifyString(resp);
			Assert.assertNotSame((statusObj.isNull("traceback") && statusObj.isNull("exception")), true, "Making sure sync resulted in traceback or exception.");
		}
		catch (JSONException jsone) {
			jsone.printStackTrace();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Test (groups={"testRepo"}, dependsOnMethods={"cancelFinishedSync"})
	public void deleteScheduledSyncRepo() {
		// Create local repo instead of using DataProvider because we want the schedule field
		// needs to be soon.
		try {
			// Create
			JSONObject requestBody = new JSONObject();
			requestBody.put("id", "api_scheduled_repo");
			requestBody.put("name", "api_scheduled_repo");
			requestBody.put("arch", "noarch");

			JSONObject scheduleObj = new JSONObject();
			JSONObject intervalObj = new JSONObject();
			intervalObj.put("minutes", "3");
			scheduleObj.put("interval", intervalObj);
		
			requestBody.put("sync_schedule", scheduleObj);
			servertasks.executePOST(functionality, "", requestBody.toString(), 201);

			// Delete
			servertasks.executeDELETE(functionality, "api_scheduled_repo", 202); 

			// Check
			Thread.currentThread().sleep(3000);
			servertasks.verifySyncStopped();
		}
		catch (JSONException jsone) {
			jsone.printStackTrace();
		}
		catch (InterruptedException ie) {
			ie.printStackTrace();
		}
	}

	// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	

	// Bugzilla Spawned Tests
	// --------------------------------------------------------------------------
	
	@Test(groups={"testRepo"}, dependsOnMethods={"cancelFinishedSync"})
	public void multiSync() {
		ArrayList<Hashtable<String, String>> repoTaskIdList = new ArrayList<Hashtable<String, String>>();

		try {
			for (List<Object> item : getMultiSyncRepoData()) {
				Hashtable fields = (Hashtable)item.get(0);
				String taskId = executeRepoSync((String)fields.get("id"));
				Assert.assertNotNull(taskId, "Making sure we got a sync id back for each spam item.");

				Hashtable<String, String> repoTaskId = new Hashtable<String, String>();
				repoTaskId.put("id", (String)fields.get("id"));
				repoTaskId.put("taskId", taskId);
				repoTaskIdList.add(repoTaskId);
			}

			for (Hashtable<String, String> repoTaskId : repoTaskIdList) {
				String resp = servertasks.executeGET(functionality, repoTaskId.get("id") + "/sync/" + repoTaskId.get("taskId"), 200);
				JSONObject statusObj = JSONifyString(resp);
				Assert.assertTrue((statusObj.isNull("traceback") && statusObj.isNull("exception")), "Making sure sync didn't result in traceback nor exception.");
			}
		}
		catch (JSONException jsone) {
			jsone.printStackTrace();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			// Cancel sync before this cause an issue trying to delete a repo that's still syncing...
			/*
				for (Hashtable<String, String> repoTaskId : repoTaskIdList) {
					// TODO: Use cancelSync() method?	
					String resp = servertasks.executeGET(functionality, repoTaskId.get("id") + "/sync/" + repoTaskId.get("taskId"), 200);
					JSONObject statusObj = JSONifyString(resp);
					if (statusObj.get("state").equals("running")) { // ok it's in the middle of running
						// execute cancel
						resp = servertasks.executeDELETE(functionality, repoTaskId.get("id") + "/sync/" + repoTaskId.get("taskId"), 202);
					}
				}
			*/
			for (List<Object> item : getMultiSyncRepoData()) {
				Hashtable fields = (Hashtable)item.get(0);
				cancelSync((String)fields.get("id"));
			}
		}
	}

	// BZ 695707
	@Test(groups={"testRepo"}, dependsOnMethods={"cancelFinishedSync"})
	public void deleteWhileSync() {
		List repoList = (List)getBigRepoData();
		List item = (List)repoList.get(0);
		Hashtable fields = (Hashtable)item.get(0);
		String id = (String)fields.get("id");
		try {
			String taskId = executeRepoSync(id);
			while (true) {
				String resp = servertasks.executeGET(functionality, id + "/sync/" + taskId, 200);
				JSONObject statusObj = JSONifyString(resp);

				if (statusObj.get("state").equals("running")) { // ok it's in the middle of running
					break;
				}
			}

			// I'd expect this initial call to fail and return something along the line of
			// "need to run cancel_sync before delete"
			String deleteResp = servertasks.executeDELETE(functionality, id, 409); // uhh is this the right code?

			// Cancel Sync
			servertasks.executeDELETE(functionality, id + "/sync/" + taskId, 202);

			// Verify the sync is actually stopped
			Assert.assertTrue(servertasks.verifySyncStopped(), "Making sure sync stopped");

			// Finally, delete
			servertasks.executeDELETE(functionality, id, 200); 
		}
		catch (JSONException jsone) {
			jsone.printStackTrace();
		}
	}

	// --------------------------------------------------------------------------

	@Test (groups={"testRepo"}, dependsOnMethods={"multiSync", "syncSpam", "syncStatusSpam", "syncBotchedRepo", "deleteWhileSync", "deleteScheduledSyncRepo"}, alwaysRun=true)
	public void cancelAllActiveSyncs() {
		for (List<Object> item : getLocalRepoData()) {
			Hashtable fields = (Hashtable)item.get(0);
			String resp = cancelSync((String)fields.get("id"));
			Assert.assertNotNull(resp);
		}

		for (List<Object> item : getBigRepoData()) {
			Hashtable fields = (Hashtable)item.get(0);
			String resp = cancelSync((String)fields.get("id"));
			Assert.assertNotNull(resp);
		}

		for (List<Object> item : getBotchedRepoData()) {
			Hashtable fields = (Hashtable)item.get(0);
			String resp = cancelSync((String)fields.get("id"));
			Assert.assertNotNull(resp);
		}

		for (List<Object> item : getMultiSyncRepoData()) {
			Hashtable fields = (Hashtable)item.get(0);
			String resp = cancelSync((String)fields.get("id"));
			Assert.assertNotNull(resp);
		}
	}

	@Test (groups={"testRepo"}, dependsOnMethods={"cancelAllActiveSyncs"}, alwaysRun=true)
	public void deleteTestRepo() {
		for (List<Object> item : getLocalRepoData()) {
			Hashtable fields = (Hashtable)item.get(0);
			String resp = servertasks.executeDELETE(functionality, (String)fields.get("id"), 200);
			Assert.assertNotNull(resp);
		}

		for (List<Object> item : getBigRepoData()) {
			Hashtable fields = (Hashtable)item.get(0);
			String resp = servertasks.executeDELETE(functionality, (String)fields.get("id"), 200);
			Assert.assertNotNull(resp);
		}

		for (List<Object> item : getBotchedRepoData()) {
			Hashtable fields = (Hashtable)item.get(0);
			String resp = servertasks.executeDELETE(functionality, (String)fields.get("id"), 200);
			Assert.assertNotNull(resp);
		}

		for (List<Object> item : getMultiSyncRepoData()) {
			Hashtable fields = (Hashtable)item.get(0);
			String resp = servertasks.executeDELETE(functionality, (String)fields.get("id"), 200);
			Assert.assertNotNull(resp);
		}
	}

	@Test (groups={"testRepo"}, dependsOnMethods={"deleteTestRepo"}, alwaysRun=true)
	public void verifyDeleteTestRepo() {
		String resp = servertasks.executeGET(functionality, "", 200);
		try {
			JSONArray repos = JSONifyArray(resp);
			Assert.assertEquals(repos.length(), 0, "Making sure the system is clean");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	// Utility Methods
	// ===========================================================================
	public String executeRepoSync(String id) throws JSONException {
		JSONObject requestBody = new JSONObject(); 
		String resp = servertasks.executePOST(functionality, id + "/sync/", requestBody.toString(), 202);
		Assert.assertNotNull(resp);
		String taskId = "";
		JSONObject syncObj = JSONifyString(resp);
		taskId = (String)syncObj.get("id");
		return taskId;	
	}

	public String executeRepoSync(String id, int expectedRtnCode) throws JSONException {
		JSONObject requestBody = new JSONObject(); 
		String resp = servertasks.executePOST(functionality, id + "/sync/", requestBody.toString(), expectedRtnCode);
		Assert.assertNotNull(resp);
		String taskId = "";
		JSONObject syncObj = JSONifyString(resp);
		System.out.println(syncObj);
		taskId = (String)syncObj.get("id");
		return taskId;	
	}

	public String executeRepoSync(String id, JSONObject requestBody) throws JSONException {
		String resp = servertasks.executePOST(functionality, id + "/sync/", requestBody.toString(), 202);
		Assert.assertNotNull(resp);
		Assert.assertNotNull(resp);
		String taskId = "";
		JSONObject syncObj = JSONifyString(resp);
		taskId = (String)syncObj.get("id");
		return taskId;	
	}

	// Unlike the one in the test, we allow id to be passed in, thus enable us to use it
	// as a utility.
	//
	// This method will cancel all active sync for a given repo
	//
	// @return the last active sync that got cancelled
	public String cancelSync(String id) {
		String resp = null;
		try {
				// Check for active syncs
				String historyResp = servertasks.executeGET(functionality, id + "/sync/", 200);
				JSONArray historyList = JSONifyArray(historyResp);	
				for (int j=0; j < historyList.length(); j++) {
					JSONObject obj = new JSONObject(historyList.get(j).toString());
					if (obj.get("state").equals("running")) {
						// active sync, cancel it
						// 1) fetch taskId
						//String status_path = obj.get("status_path").toString();
						//String taskId = status_path.split("sync/", 2)[1].replace("/", "");
						String taskId = obj.get("id").toString();

						// 2) Cancel
						resp = servertasks.executeDELETE(functionality, id + "/sync/" + taskId, 202);
					}
				}
		}
		catch (JSONException jsone) {
			jsone.printStackTrace();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return resp;
	}
	// ===========================================================================

	@DataProvider(name="localRepoData")
	public Object[][] localRepoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalRepoData());
	}
	public List<List<Object>> getLocalRepoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();

		Hashtable<String, String> fields = new Hashtable<String, String>();
		fields.put("id", "api_test_repo");
		fields.put("name", "api_test_repo");
		fields.put("arch", "noarch");
		fields.put("feed", "http://10.16.76.78/pub/updates");

		data.add(Arrays.asList(new Object[]{fields}));
		return data;
	}

	@DataProvider(name="bigRepoData")
	public Object[][] bigRepoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getBigRepoData());
	}
	public List<List<Object>> getBigRepoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();

		Hashtable<String, String> fields = new Hashtable<String, String>();
		fields.put("id", "api_big_repo");
		fields.put("name", "api_big_repo");
		fields.put("arch", "noarch");
		fields.put("feed", "http://download.devel.redhat.com/released/RHEL-6/6.0/Server/x86_64/os/");

		data.add(Arrays.asList(new Object[]{fields}));
		return data;
	}

	@DataProvider(name="botchedRepoData")
	public Object[][] botchedRepoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getBotchedRepoData());
	}
	public List<List<Object>> getBotchedRepoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();

		Hashtable<String, String> fields = new Hashtable<String, String>();
		fields.put("id", "api_botched_repo");
		fields.put("name", "api_botched_repo");
		fields.put("arch", "noarch");
		fields.put("feed", "http://download.devel.redhat.com/released/RHEL-6/6.0/Server/x86_/os/");

		data.add(Arrays.asList(new Object[]{fields}));
		return data;
	}

	// TODO: Is it really necessary to have YET another DataProvider?
	// Some data overlaps, can't I find some clever way to reuse them?
	// ...............................................................
	// Nope I got nothing.........................doh! (>_<)
	@DataProvider(name="multiSyncRepoData")
	public Object[][] multiSyncRepoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getMultiSyncRepoData());
	}
	public List<List<Object>> getMultiSyncRepoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();

		Hashtable<String, String> fields = new Hashtable<String, String>();
		fields.put("id", "api_multisync_repo_1");
		fields.put("name", "api_multisync_repo_1");
		fields.put("arch", "noarch");
		fields.put("feed", "http://download.devel.redhat.com/released/RHEL-6/6.0/Server/x86_64/os/");

		data.add(Arrays.asList(new Object[]{fields}));

		fields = new Hashtable<String, String>();
		fields.put("id", "api_multisync_repo_2");
		fields.put("name", "api_multisync_repo_2");
		fields.put("arch", "noarch");
		fields.put("feed", "http://download.devel.redhat.com/released/F-14/GOLD/Fedora/x86_64/os/");

		data.add(Arrays.asList(new Object[]{fields}));

		return data;
	}
}
