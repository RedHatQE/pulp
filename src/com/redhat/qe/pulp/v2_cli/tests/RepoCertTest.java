package com.redhat.qe.pulp.v2_cli.tests;

import com.redhat.qe.pulp.v2_cli.tasks.PulpTasks;
import com.redhat.qe.pulp.v2_cli.base.PulpTestScript;

import org.testng.annotations.Test;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import com.redhat.qe.auto.testng.TestNGUtils;
import com.redhat.qe.Assert;
import org.testng.annotations.DataProvider;

import com.redhat.qe.tools.SSHCommandRunner;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Hashtable;

public class RepoCertTest extends PulpTestScript {
	private String certPath = "/etc/pki/content/big-content-cert.crt";
	private String keyPath = "/etc/pki/content/big-content-key.pem";
	private String caCertPath = "/etc/pki/content/cdn.redhat.com-chain.crt";

	public RepoCertTest() {
		super();
	}

	@BeforeClass(groups="testRepoCert")
	public void generateCert() {
		if (Boolean.parseBoolean(reinstall_flag)) {
			// In the future when we have our own CDN QA certs,
			// wget the certs instead of relying on cert being on the system.
			server.runCommandAndWait("sed -i s/num\\_old\\_pkgs\\_keep:\\ 2/num\\_old\\_pkgs\\_keep:\\ 20/g /etc/pulp/pulp.conf");
		}
		server.runCommandAndWait("cd /tmp && wget -N http://qeblade20.rhq.lab.eng.bos.redhat.com/pub/RPMCheck.py");
		client.runCommandAndWait("cd /tmp && wget -N http://qeblade20.rhq.lab.eng.bos.redhat.com/pub/RPMCheck.py");
	}

	@Test(groups="testRepoCert") 
	public void createProtectedRepo() {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			servertasks.createTestRepoWithMetadata(repoData);
		}
	}

	@Test(groups="testRepoCert", dataProvider="localRepoData", dependsOnMethods={"createProtectedRepo"})
	public void syncProtectedRepo(ArrayList<String> repoOpts, String xmlMetadataBaseURL) {
		String repoId = repoOpts.get(0).replace("--id=", "");
		servertasks.syncTestRepo(repoId, true);
	}

	@Test(groups="testRepoCert", dataProvider="localRepoData", dependsOnMethods={"syncProtectedRepo"})
	public void verifySync(ArrayList<String> repoOpts, String baseXMLMetadataURL) {
		// Wow hardcoding repo element number, that's hackish...
		// /equip rear guard...
		String repoId = repoOpts.get(0).replace("--id=", "");

		Assert.assertTrue(verifyContent(repoId, baseXMLMetadataURL), "Check if content is pulled down correctly and metadata is created properly.");
	}
	
	@Test(groups="testRepoCert") 
	public void deleteProtectedRepo() {
		for (List<Object> repo : getLocalRepoData()) {
			ArrayList repoData = (ArrayList)repo.get(0);
			String repoId = ((String)repoData.get(0)).replace("--id=", "");
			servertasks.deleteTestRepo(repoId);
		}
	}

	// Note: I want to make sure a small simple sync work before proceeding
	// to a more complicated sceanrio.
	@Test(groups="testRepoCert", dependsOnMethods={"deleteProtectedRepo"})
	public void parallelSyncProtectedRepo() {
		// TODO: Adjustments to max threads?
		int[] stages = new int[]{2,4,8,16};

		for (int numSyncs : stages) {
			ArrayList<SSHCommandRunner> runners = new ArrayList<SSHCommandRunner>();
			ArrayList<Hashtable<String, String>> verifyRepoData = new ArrayList<Hashtable<String, String>>();

			for(int i=0;i<numSyncs;i++) {
				// Create repo and kick off sync
				List<Object> repo = getLocalParallelRepoData().get(i);
				try {
					ArrayList repoData = (ArrayList)repo.get(0);
					String xmlURL = (String)repoData.get(1);
					String repoId = ((String)repoData.get(0)).replace("--id=", "") + i;

					// TODO: Find a more elegant solution to cloning repoData and 
					// replacing the repoId w/ a modded one.
					ArrayList<String> localRepoData = (ArrayList<String>)repoData.clone();
					localRepoData.set(0, repoId); // ugh..hardcode

					servertasks.createTestRepoWithMetadata(repoData); // Create

					SSHCommandRunner r = new SSHCommandRunner(serverHostname, sshUser, sshPassphrase, sshKeyPrivate, sshKeyPassphrase, null);
					r.runCommand("pulp-admin repo sync --id=" + repoId + " -F"); // Sync

					// Store up data for later use
					runners.add(r);
					Hashtable<String, String> data = new Hashtable<String, String>();
					data.put("id", repoId);
					data.put("base_url", xmlURL);
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}

			// Wait for them to finish
			for (SSHCommandRunner r : runners) {
				r.waitFor();
				try {
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
			}

			// Batch verification of all the syncs we kicked off
			// Note: This will take forever...forever ever, ever ever...
			for (Hashtable<String, String> data : verifyRepoData) {
				String repoId = data.get("id");
				String baseXMLMetadataURL = data.get("base_url");
				Assert.assertTrue(verifyContent(repoId, baseXMLMetadataURL), "Check if content is pulled down correctly and metadata is created properly.");
			}

			// Cleanup
			for (Hashtable<String, String> data : verifyRepoData) {
				String repoId = data.get("id");
				servertasks.deleteTestRepo(repoId);
			}
		}
	}

	// TODO:
	// grinder via cli


	// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// Util Methods
	public boolean verifyContent(String repoId, String xmlFileBaseURL) {
		// Clean up old repomd.xml file
		servertasks.execCmd("rm -vf /tmp/repomd.xml");

		// Fetch the metadata file
		servertasks.localFetchFile(certPath, keyPath, caCertPath, xmlFileBaseURL + "/repodata/repomd.xml", "/tmp");
		String primaryXMLFName = servertasks.getPrimaryXMLFileName("/tmp/repomd.xml"); // ugh hardcode
		servertasks.fetchFile(certPath, keyPath, caCertPath, xmlFileBaseURL + primaryXMLFName, "/tmp");
		clienttasks.fetchFile(certPath, keyPath, caCertPath, xmlFileBaseURL + primaryXMLFName, "/tmp");

		String fresult = servertasks.execCmd("python /tmp/RPMCheck.py -f /tmp/" + primaryXMLFName.replace("repodata/", "") + " -r " + repoId + " -s");
		Assert.assertFalse(fresult.contains("Error"), "Making sure rpm file check doesn't contain any error.");

		// temp bind
		ArrayList<String> yumOutputs = new ArrayList<String>();
		for (List<Object> consumer : getConsumerData()) {
			String consumerId = (String)consumer.get(0);
			PulpTasks task = (PulpTasks)consumer.get(1);
			task.bindConsumer(consumerId, repoId, true);

			String yresult = task.execCmd("python /tmp/RPMCheck.py -f /tmp/" + primaryXMLFName.replace("repodata/", "") + " -r " + repoId + " -y");
			Assert.assertFalse(yresult.contains("Error"), "Making sure rpm file check doesn't contain any error.");
		}

		return true;
	}
	// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	
	@DataProvider(name="localRepoData")
	public Object[][] localRepoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalRepoData());
	}
	public List<List<Object>> getLocalRepoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();

		ArrayList<String> repoOpts = new ArrayList<String>();
		
		repoOpts.add("--id=protected_test_repo");
		repoOpts.add("--feed=https://cdn.redhat.com/content/dist/rhel/rhui/server/5Server/x86_64/rhui/1.1/os");
		repoOpts.add("--feed_ca=/etc/pki/content/cdn.redhat.com-chain.crt");
		repoOpts.add("--feed_cert=/etc/pki/content/big-content-cert.crt");
		repoOpts.add("--feed_key=/etc/pki/content/big-content-key.pem");

		data.add(Arrays.asList(new Object[]{repoOpts, "https://cdn.redhat.com/content/dist/rhel/rhui/server/5Server/x86_64/rhui/1.1/os/"}));		

		return data;
	}

	@DataProvider(name="localParallelRepoData")
	public Object[][] localParallelRepoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalParallelRepoData());
	}
	public List<List<Object>> getLocalParallelRepoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();

		ArrayList<String> repoOpts = new ArrayList<String>();
		
		repoOpts.add("--id=protected_test_parallel_repo_A");
		repoOpts.add("--feed=https://cdn.redhat.com/content/dist/rhel/rhui/server/5Server/x86_64/os");
		repoOpts.add("--feed_ca=" + caCertPath);
		repoOpts.add("--feed_cert=" + certPath);
		repoOpts.add("--feed_key=" + keyPath);

		data.add(Arrays.asList(new Object[]{repoOpts, "https://cdn.redhat.com/content/dist/rhel/rhui/server/5Server/x86_64/os/"}));		

		repoOpts = new ArrayList<String>();

		repoOpts.add("--id=protected_test_parallel_repo_B");
		repoOpts.add("--feed=https://cdn.redhat.com/content/dist/rhel/rhui/server-6/updates/6Server/x86_64/os");
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
