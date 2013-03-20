/* Purpose: To measure performance for repo related CLI calls in Pulp.
 *
 * TODO: Take measurement of non-sync functions
 *		   - Make gather_stat.py run in the background? 
 *		   - Collect local data to correlate between calls and stats
 * TODO: Make adjustments to num_threads/concurrent_sync in pulp.conf
 */

package com.redhat.qe.pulp.perf.tests;

import java.util.Hashtable;

import java.util.logging.Logger;

import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import com.redhat.qe.auto.testng.TestNGUtils;
import com.redhat.qe.Assert;

import com.redhat.qe.pulp.perf.tasks.PerfPulpTasks;
import com.redhat.qe.pulp.perf.base.PerfPulpTestScript;

import com.redhat.qe.pulp.abstraction.PulpAbstraction;
import com.redhat.qe.tools.SSHCommandRunner;
import com.redhat.qe.tools.SCPTools;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class RepoPerfTest extends PerfPulpTestScript {
	protected String automationHome			= System.getProperty("pulp.automation.home");
	protected String pkgValidation			= System.getProperty("pulp.pkg.validate", "false");
	protected static Logger log = Logger.getLogger(RepoPerfTest.class.getName());

	private String certPath = "/etc/pki/content/big-content-cert.crt";
	private String keyPath = "/etc/pki/content/big-content-key.pem";
	private String caCertPath = "/etc/pki/content/cdn.redhat.com-chain.crt";

	public RepoPerfTest() {
		super();
	}

	@BeforeClass(groups="repoPerfTest")
	public void generateCert() {
		if (Boolean.parseBoolean(reinstall_flag)) {
			// In the future when we have our own CDN QA certs,
			// wget the certs instead of relying on cert being on the system.
			server.runCommandAndWait("sed -i s/num\\_old\\_pkgs\\_keep:\\ 2/num\\_old\\_pkgs\\_keep:\\ 20/g /etc/pulp/pulp.conf");
		}
		server.runCommandAndWait("rm -vf /tmp/stat_*");
		server.runCommandAndWait("rm -vf /var/www/html/stat_*");
		
		server.runCommandAndWait("cd /tmp && wget -N http://10.16.76.78/pub/RPMCheck.py");
		client.runCommandAndWait("cd /tmp && wget -N http://10.16.76.78/pub/RPMCheck.py");

		SCPTools scpTool = new SCPTools(serverHostname, sshUser, sshKeyPrivate, sshKeyPassphrase);
		scpTool.sendFile(automationHome+"perf/base/gather_stat.py", "/tmp");
	}

	@Test(groups="repoPerfTest", dataProvider="parallelSyncLoopData")
	public void parallelSyncRepo(int numSyncs) throws AssertionError, IOException {
		System.out.println("++++++++++++++++++++++++++++++++++++++++++++");
		System.out.println("  Stages = " + numSyncs);
		System.out.println("  Start Time: " + System.currentTimeMillis());
		System.out.println("++++++++++++++++++++++++++++++++++++++++++++");

		ArrayList<Hashtable<String, String>> verifyRepoData = null;
		ArrayList<SSHCommandRunner> runners = null;

		try {
			verifyRepoData = createParallelRepos(numSyncs);
			runners = syncRepos(verifyRepoData);
			waitForSyncs(runners, numSyncs);

			// Note: This will take forever...forever ever, ever ever...
			if (Boolean.parseBoolean(pkgValidation)) {
				for (Hashtable<String, String> data : verifyRepoData) {
					String repoId = data.get("id");
					String baseXMLMetadataURL = data.get("base_url");
					Assert.assertTrue(verifyContent(repoId, baseXMLMetadataURL), "Check if content is pulled down correctly and metadata is created properly.");
				}
			}
		} catch (AssertionError ae) {
			ae.printStackTrace();
			throw ae;
		} catch (IOException ioe) {
			ioe.printStackTrace();
			throw ioe;
		} finally {
			// Cleanup
			for (Hashtable<String, String> data : verifyRepoData) {
				String repoId = data.get("id");
				servertasks.deleteTestRepo(repoId);
			}
			System.out.println("++++++++++++++++++++++++++++++++++++++++++++");
			System.out.println("  End Time: " + System.currentTimeMillis());
			System.out.println("++++++++++++++++++++++++++++++++++++++++++++");
		}
	}

	@Test(groups="repoPerfTest", dataProvider="parallelSyncLoopData", dependsOnMethods={"parallelSyncRepo"}, alwaysRun=true)
	public void fetchAndProcessStats(int numSyncs) {
		String testHome = System.getProperty("automation.dir");
		String testOutputDir = testHome + "/test-output/PulpPerformanceTest/";
		servertasks.localFetchFile("http://" + serverHostname + "/stat_" + numSyncs, testOutputDir);
		try {
			String line;
			Runtime r = Runtime.getRuntime();
			String cmd = "python generate_graphs.py -i " + testOutputDir + "/stat_" + numSyncs + " -o " + testOutputDir + "/stat_" + numSyncs + ".png";
			log.info(cmd);
			Process p = r.exec(cmd, null, new File(testHome + "/src/com/redhat/qe/pulp/perf/base/"));

			p.waitFor();
			BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
			while ((line = input.readLine()) != null) {
				log.severe(line);
			}
			input.close();

			input = new BufferedReader(new InputStreamReader(p.getErrorStream()));
			while ((line = input.readLine()) != null) {
				log.severe(line);
			}
			input.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@AfterClass(groups="repoPerfTest")
	public void cleanAll() {
		for(int i=0;i<getParallelSyncLoopData().size();i++) {
			// Create repo and kick off sync
			List<Object> repo = getParallelRepoData().get(i % getParallelRepoData().size());
			try {
				ArrayList repoData = (ArrayList)repo.get(0);
				String xmlURL = (String)repoData.get(1);
				String repoId = ((String)repoData.get(0)).replace("--id=", "") + i;
				servertasks.deleteTestRepo(repoId);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// Util Methods
	public ArrayList<Hashtable<String, String>> createParallelRepos(int numRepos) throws AssertionError{
		ArrayList<Hashtable<String, String>> verifyRepoData = new ArrayList<Hashtable<String, String>>();
		for(int i=0;i<numRepos;i++) {
			// Create repo and kick off sync
			List<Object> repo = getParallelRepoData().get(i % getParallelRepoData().size());
			try {
				ArrayList repoData = (ArrayList)repo.get(0);
				String xmlURL = (String)repoData.get(1);
				String repoId = ((String)repoData.get(0)).replace("--id=", "") + i;

				// TODO: Find a more elegant solution to cloning repoData and 
				// replacing the repoId w/ a modded one.
				ArrayList<String> localRepoData = (ArrayList<String>)repoData.clone();
				localRepoData.set(0, "--id=" + repoId); // ugh..hardcode

				Hashtable<String, String> data = new Hashtable<String, String>();
				data.put("id", repoId);
				data.put("base_url", xmlURL.replace("--feed=", ""));
				verifyRepoData.add(data);

				servertasks.createTestRepoWithMetadata(localRepoData); // Create
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return verifyRepoData;
	}

	public ArrayList<SSHCommandRunner> syncRepos(ArrayList<Hashtable<String, String>> repoData) throws AssertionError, IOException {
		ArrayList<SSHCommandRunner> runners = new ArrayList<SSHCommandRunner>();
		for (Hashtable<String, String> data: repoData) {
			String repoId = data.get("id");
			SSHCommandRunner r = new SSHCommandRunner(serverHostname, sshUser, sshPassphrase, sshKeyPrivate, sshKeyPassphrase, null);
			r.runCommand("pulp-admin repo sync --id=" + repoId + " -F"); // Sync
			runners.add(r);
		}
		return runners;
	}

	public void waitForSyncs(ArrayList<SSHCommandRunner> runners, int numSyncs) throws AssertionError {
		for (SSHCommandRunner r : runners) {
			try {
				while (!r.isDone()) {
					String output = servertasks.execCmd("cd /tmp && python gather_stat.py -w -n " + numSyncs);
					Thread.currentThread().sleep(60000);
				}
				servertasks.execCmd("mv /tmp/stat* /var/www/html/");

				Assert.assertEquals(r.getExitCode(), Integer.valueOf(0), "Making sure exit code is zero.");
				Assert.assertFalse(r.getStdout().contains("error") || r.getStdout().contains("traceback") ||
						r.getStderr().contains("error") || r.getStderr().contains("traceback"), 
						"Making sure stdout from sync doesn't contain error or traceback");
			}
			catch (AssertionError ae) {
				log.info("===================================================");
				log.info(r.getStdout());
				log.info(r.getStderr());
				log.info("===================================================");
				throw ae;
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public boolean verifyContent(String repoId, String xmlFileBaseURL) throws AssertionError {
		// Clean up old repomd.xml file
		servertasks.execCmd("rm -vf /tmp/repomd.xml");

		// Fetch the metadata file
		servertasks.localFetchFile(certPath, keyPath, caCertPath, xmlFileBaseURL + "/repodata/repomd.xml", "/tmp");
		String primaryXMLFName = servertasks.getPrimaryXMLFileName("/tmp/repomd.xml"); // ugh hardcode
		servertasks.fetchFile(certPath, keyPath, caCertPath, xmlFileBaseURL + primaryXMLFName, "/tmp");
		//clienttasks.fetchFile(certPath, keyPath, caCertPath, xmlFileBaseURL + primaryXMLFName, "/tmp");

		String fresult = servertasks.execCmd("python /tmp/RPMCheck.py -f /tmp/" + primaryXMLFName.replace("repodata/", "") + " -r " + repoId + " -s");
		Assert.assertFalse(fresult.contains("Error"), "Making sure rpm file check doesn't contain any error.");

		// temp bind
		ArrayList<String> yumOutputs = new ArrayList<String>();
		for (List<Object> consumer : getConsumerData()) {
			String consumerId = (String)consumer.get(0);
			PerfPulpTasks task = (PerfPulpTasks)consumer.get(1);
			task.bindConsumer(consumerId, repoId, true);

			String yresult = task.execCmd("python /tmp/RPMCheck.py -f /tmp/" + primaryXMLFName.replace("repodata/", "") + " -r " + repoId + " -y");
			Assert.assertFalse(yresult.contains("Error"), "Making sure rpm file check doesn't contain any error.");
		}

		return true;
	}
	// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

	@DataProvider(name="parallelSyncLoopData")
	public Object[][] parallelSyncLoopData() {
		return TestNGUtils.convertListOfListsTo2dArray(getParallelSyncLoopData());
	}
	public List<List<Object>> getParallelSyncLoopData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		data.add(Arrays.asList(new Object[]{2}));		
		data.add(Arrays.asList(new Object[]{4}));		
		data.add(Arrays.asList(new Object[]{8}));		
		data.add(Arrays.asList(new Object[]{16}));		
		return data;
	}

	@DataProvider(name="parallelRepoData")
	public Object[][] parallelRepoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getParallelRepoData());
	}
	public List<List<Object>> getParallelRepoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();

		ArrayList<String> repoOpts = new ArrayList<String>();
		
		repoOpts.add("--id=protected_test_parallel_repo_A");
		repoOpts.add("--feed=https://cdn.redhat.com/content/dist/rhel/rhui/server/5Server/x86_64/os/");
		repoOpts.add("--feed_ca=" + caCertPath);
		repoOpts.add("--feed_cert=" + certPath);
		repoOpts.add("--feed_key=" + keyPath);

		data.add(Arrays.asList(new Object[]{repoOpts, "https://cdn.redhat.com/content/dist/rhel/rhui/server/5Server/x86_64/os/"}));		

		repoOpts = new ArrayList<String>();

		repoOpts.add("--id=protected_test_parallel_repo_B");
		repoOpts.add("--feed=https://cdn.redhat.com/content/dist/rhel/rhui/server-6/updates/6Server/x86_64/os/");
		repoOpts.add("--feed_ca=" + caCertPath);
		repoOpts.add("--feed_cert=" + certPath);
		repoOpts.add("--feed_key=" + keyPath);

		data.add(Arrays.asList(new Object[]{repoOpts, "https://cdn.redhat.com/content/dist/rhel/rhui/server-6/updates/6Server/x86_64/os/Packages/"}));		

		repoOpts = new ArrayList<String>();

		repoOpts.add("--id=protected_test_parallel_repo_C");
		repoOpts.add("--feed=http://download.devel.redhat.com/released/F-15/GOLD/Fedora/x86_64/os/");

		data.add(Arrays.asList(new Object[]{repoOpts, "http://download.devel.redhat.com/released/F-15/GOLD/Fedora/x86_64/os/"}));		

		repoOpts = new ArrayList<String>();

		repoOpts.add("--id=protected_test_parallel_repo_D");
		repoOpts.add("--feed=http://download.devel.redhat.com/released/F-14/GOLD/Fedora/x86_64/os/");

		data.add(Arrays.asList(new Object[]{repoOpts, "http://download.devel.redhat.com/released/F-14/GOLD/Fedora/x86_64/os/"}));		

		repoOpts = new ArrayList<String>();

		repoOpts.add("--id=protected_test_parallel_repo_E");
		repoOpts.add("--feed=http://download.devel.redhat.com/released/F-13/GOLD/Fedora/x86_64/os/");

		data.add(Arrays.asList(new Object[]{repoOpts, "http://download.devel.redhat.com/released/F-13/GOLD/Fedora/x86_64/os/"}));		

		repoOpts = new ArrayList<String>();

		repoOpts.add("--id=protected_test_parallel_repo_F");
		repoOpts.add("--feed=http://download.devel.redhat.com/released/F-15/GOLD/Fedora/i386/os/");

		data.add(Arrays.asList(new Object[]{repoOpts, "http://download.devel.redhat.com/released/F-15/GOLD/Fedora/i386/os/"}));		

		repoOpts = new ArrayList<String>();

		repoOpts.add("--id=protected_test_parallel_repo_G");
		repoOpts.add("--feed=http://download.devel.redhat.com/released/F-14/GOLD/Fedora/i386/os/");

		data.add(Arrays.asList(new Object[]{repoOpts, "http://download.devel.redhat.com/released/F-14/GOLD/Fedora/i386/os/"}));		

		repoOpts = new ArrayList<String>();

		repoOpts.add("--id=protected_test_parallel_repo_H");
		repoOpts.add("--feed=http://download.devel.redhat.com/released/F-13/GOLD/Fedora/i386/os/");

		data.add(Arrays.asList(new Object[]{repoOpts, "http://download.devel.redhat.com/released/F-13/GOLD/Fedora/i386/os/"}));		

		return data;
	}
}
