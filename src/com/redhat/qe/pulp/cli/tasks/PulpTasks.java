package com.redhat.qe.pulp.cli.tasks;

import java.util.logging.Logger;

import java.util.Hashtable;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObject;

import java.io.FileReader;

//import com.redhat.qe.auto.testng.Assert;
import com.redhat.qe.Assert;
import com.redhat.qe.tools.SSHCommandRunner;

public class PulpTasks {
	protected static Logger log = Logger.getLogger(PulpTasks.class.getName());
	protected SSHCommandRunner sshCommandRunner = null;

	ClassLoader parent;
	GroovyClassLoader loader;
	Class groovyClass;
	GroovyObject groovyObject;

	public PulpTasks(SSHCommandRunner runner) {
		setSSHCommandRunner(runner);

		try {
			parent = getClass().getClassLoader();
			loader = new GroovyClassLoader(parent);
			groovyClass = loader.parseClass(new File(System.getProperty("pulp.automation.home") + "/abstraction/YumMetadataParser.groovy"));
			groovyObject = (GroovyObject) groovyClass.newInstance();
		}
		catch (Exception e) {
			e.printStackTrace();
		}

	}

	public void setSSHCommandRunner(SSHCommandRunner runner) {
		sshCommandRunner = runner;
	}

	public String createConsumer(String consumerId, boolean expectSuccess) {
		if (expectSuccess) {
			Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-consumer -u admin -p admin consumer register --id "+consumerId).getExitCode(), Integer.valueOf(0), "Creating consumer: ");
		}
		else {
			Assert.assertNotSame(sshCommandRunner.runCommandAndWait("pulp-consumer -u admin -p admin consumer register --id "+consumerId).getExitCode(), Integer.valueOf(0), "Creating consumer: ");
		}
		return sshCommandRunner.getStdout();
	}

	public String bindConsumer(String consumerId, String repoId, boolean expectSuccess) {
		if (expectSuccess) {
			Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-consumer consumer bind --repoid "+repoId).getExitCode(), Integer.valueOf(0), "Binding consumer to repo: ");
		}
		else {
			Assert.assertNotSame(sshCommandRunner.runCommandAndWait("pulp-consumer consumer bind --repoid "+repoId).getExitCode(), Integer.valueOf(0), "Binding consumer to repo: ");
		}
		return sshCommandRunner.getStderr();
	}

	public String clientDeleteConsumer(boolean expectSuccess) {
		if (expectSuccess) {
			Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-consumer consumer unregister").getExitCode(), Integer.valueOf(0), "Deleting consumer: ");
		}
		else {
			Assert.assertNotSame(sshCommandRunner.runCommandAndWait("pulp-consumer consumer unregister").getExitCode(), Integer.valueOf(0), "Deleting consumer: ");
		}
		return sshCommandRunner.getStderr();
	}

	public void deleteConsumer(String consumerId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin consumer unregister --id=" + consumerId).getExitCode(), Integer.valueOf(0), "Deleting consumer: ");
	}

	public void clientUnbindConsumer(String repoId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin consumer unbind --repoid "+repoId).getExitCode(), Integer.valueOf(0), "Unbinding consumer from repo");
	}

	public void unbindConsumer(String consumerId, String repoId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin consumer unbind --repoid "+repoId + " --id=" + consumerId).getExitCode(), Integer.valueOf(0), "Unbinding consumer from repo");
	}


	public void login() {
		sshCommandRunner.runCommandAndWait("pulp-admin -u admin -p admin auth login -u admin -p admin");
		Assert.assertEquals(sshCommandRunner.getExitCode(), Integer.valueOf(0), "Logging in: ");
	}

	public void logout() {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin auth logout").getExitCode(), Integer.valueOf(0), "Loging out: ");
	}
	
	public String packageSearch(String pkgName) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("rpm -qa \"" + pkgName + "\"").getExitCode(), Integer.valueOf(0), "Searching for pkg "+pkgName);
		return sshCommandRunner.getStdout();
	}

	public void uninstallPkg(String pkgName) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("yum remove -y "+pkgName).getExitCode(), Integer.valueOf(0), "Removing rpm package "+pkgName);
	}

	public String listConsumer() {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin consumer list").getExitCode(), Integer.valueOf(0));
		return sshCommandRunner.getStdout();
	}

	public String pollTransactions() {
		sshCommandRunner.runCommandAndWait("ps -elf | grep \"/usr/bin/yum\" | grep --invert-match \"grep\"");
		return sshCommandRunner.getStdout();
	}

	public String installPackage(String pkgName, String consumerId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin package install -n "+pkgName+" --consumerid=" + consumerId).getExitCode(), Integer.valueOf(0), "Installing pkg on consumer "+consumerId);
		return sshCommandRunner.getStdout();
	}

	public String installPkgGroup(String pkgGroupName, String consumerId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin packagegroup install --consumerid="+consumerId+" --id="+pkgGroupName).getExitCode(), Integer.valueOf(0), "Installing "+pkgGroupName);
		return sshCommandRunner.getStdout();
	}

	public void updatePkgInfo() {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-consumer consumer update").getExitCode(), Integer.valueOf(0), "Update consumer pkg profile.");
	}

	public String listErrataByConsumer(String consumerId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin errata list --consumerid="+consumerId).getExitCode(), Integer.valueOf(0), "Listing errata for "+consumerId);
		return sshCommandRunner.getStdout();
	}

	public String listErrataByRepo(String repoId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin errata list --repoid="+repoId).getExitCode(), Integer.valueOf(0), "Listing errata for "+repoId);
		return sshCommandRunner.getStdout();
	}

	public String errataInfo(String errataId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin errata info --id="+errataId).getExitCode(), Integer.valueOf(0), "Errata info for "+errataId);
		return sshCommandRunner.getStdout();
	}
	
	public String installErrata(String consumerId, String errataId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin errata install --consumerid="+consumerId+" -e "+errataId).getExitCode(), Integer.valueOf(0), "Installing "+errataId);
		return sshCommandRunner.getStdout();
	}

	public void cleanCache() {
		//Assert.assertEquals(sshCommandRunner.runCommandAndWait("cat /etc/yum.repos.d/pulp.repo").getExitCode(), Integer.valueOf(0));
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("yum clean all").getExitCode(), Integer.valueOf(0));
	}

	public String yumRepoList() {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("yum repolist").getExitCode(), Integer.valueOf(0));
		return sshCommandRunner.getStdout();
	}

	public String listRepoStatus(String repoId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin repo status --id="+repoId).getExitCode(), Integer.valueOf(0));
		return sshCommandRunner.getStdout();
	}

	public String syncTestRepo(String repoId, boolean follow) {
		// note: the -F flag foregrounds the command, thus enable us to detect a failure
		if (follow) {
			Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin repo sync --id "+repoId+" -F").getExitCode(), Integer.valueOf(0), "Syncing Repo");
		}
		else {
			Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin repo sync --id "+repoId).getExitCode(), Integer.valueOf(0), "Syncing Repo");
		}
		return sshCommandRunner.getStdout();
	}

	public void createTestRepo(String repoId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin repo create --id="+repoId+" --name="+repoId).getExitCode(), Integer.valueOf(0), "Creating repo: ");
	}

	public void createTestRepo(ArrayList fields) {
		StringBuffer cmd = new StringBuffer("pulp-admin repo create ");
		Iterator itr = fields.iterator();
		while (itr.hasNext()) {
			cmd.append((String)itr.next()).append(" ");
		}
		Assert.assertEquals(sshCommandRunner.runCommandAndWait(cmd.toString()).getExitCode(), Integer.valueOf(0), "Creating repo: ");
	}

	public void createTestRepoWithMetadata(ArrayList fields) {
		StringBuffer cmd = new StringBuffer("pulp-admin repo create --preserve_metadata ");
		Iterator itr = fields.iterator();
		while (itr.hasNext()) {
			cmd.append((String)itr.next()).append(" ");
		}
		Assert.assertEquals(sshCommandRunner.runCommandAndWait(cmd.toString()).getExitCode(), Integer.valueOf(0), "Creating repo: ");
	}

	public void createTestRepoWithMetadata(String repoId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin repo create --id="+repoId+" --name="+repoId + " --preserve_metadata").getExitCode(), Integer.valueOf(0), "Creating repo: ");
	}

	public void cloneTestRepo(ArrayList<String> fields) {
		StringBuffer cmd = new StringBuffer("pulp-admin repo clone ");
		Iterator itr = fields.iterator();
		while (itr.hasNext()) {
			cmd.append((String)itr.next()).append(" ");
		}
		Assert.assertEquals(sshCommandRunner.runCommandAndWait(cmd.toString()).getExitCode(), Integer.valueOf(0), "Cloning repo: ");
	}

	public String deleteRepo(String repoId) {
		sshCommandRunner.runCommandAndWait("pulp-admin repo delete --id="+repoId);
		return sshCommandRunner.getStdout() + sshCommandRunner.getStderr();
	}

	public void deleteTestRepo(String repoId) {
		String result = deleteRepo(repoId);

		if (result.contains("cannot") && result.contains("sync")) {
			// Apparently, leftover sync is still ongoing 
			cancelSyncTestRepo(repoId);
			// because cancel_sync is an async event, we need to check for status
			// continuously until it times out
			long start = System.currentTimeMillis();
			while (listRepoStatus(repoId).contains("syncing")) {
				long now = System.currentTimeMillis();
				Assert.assertTrue(((now - start) < 300000), "Waiting for 5 mins for cancel...");
			}
			result = deleteRepo(repoId);
			Assert.assertTrue(!result.contains("cannot"), "Making sure delete does not contain warning message about ongoing sync.");
			Assert.assertTrue(!result.contains("Traceback"), "Making sure delete does not contain any traceback.");
		}
		else if (result.contains("cannot be deleted") || result.contains("being deleted")) {
			// Delete task already exists, wait till it changes.
			while ((result = deleteRepo(repoId)).contains("cannot be deleted") || result.contains("being deleted")) {
				try {
					Thread.sleep(10000);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			Assert.assertTrue(result.contains("No repository"), "Making sure delete went thru.");
		}
	}

	public String listRepo(boolean expectSuccess) {
		String rtn = "";
		if (expectSuccess) {
			Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin repo list").getExitCode(), Integer.valueOf(0));
			rtn = sshCommandRunner.getStdout();
		}
		else {
			Assert.assertNotSame(sshCommandRunner.runCommandAndWait("pulp-admin repo list").getExitCode(), Integer.valueOf(0));
			rtn = sshCommandRunner.getStderr();
		}
		return rtn;
	}
	public String listConsumerGroup() {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin consumergroup list").getExitCode(), Integer.valueOf(0), "List consumer group ");
		return sshCommandRunner.getStdout();
	}
	

	public String getRepoPackageList(String repoId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin repo content --id="+repoId).getExitCode(), Integer.valueOf(0));
		return sshCommandRunner.getStdout();
	}

	public String listRepoSchedules() {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin repo schedules").getExitCode(), Integer.valueOf(0));
		return sshCommandRunner.getStdout();
	}

	public void createRelativeTestRepo(String repoLocation, String repoId, String repoArch, String relativePath, String repoSchedule) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin repo create --id="+repoId+" --name="+repoId+" --arch="+repoArch+" --feed="+repoLocation+" --schedule=\""+repoSchedule+"\""+" --relativepath="+relativePath).getExitCode(), Integer.valueOf(0), "Creating relative repo: ");
	}

	public void createCertTestRepo(String repoLocation, String repoId, String repoArch, String certPath) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin repo create --id="+repoId+" --name="+repoId+" --arch="+repoArch+" --feed="+repoLocation).getExitCode()+" --cacert="+certPath, Integer.valueOf(0), "Creating certificate repo: ");
	}

	public void cancelSyncTestRepo(String repoId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin repo cancel_sync --id="+repoId).getExitCode(), Integer.valueOf(0), "Unsyncing repo: ");
	}

/*
	public void updateTestRepo(Hashtable<String, String> fields) {
		StringBuffer cmd = new StringBuffer("pulp-admin repo update ");
		Enumeration e = fields.keys();
		while (e.hasMoreElements()) {
			cmd.append(fields.get(e.nextElement())).append(" ");
		}
		Assert.assertEquals(sshCommandRunner.runCommandAndWait(cmd.toString()).getExitCode(), Integer.valueOf(0), "Updating repo info");
	}
*/

	public String updateTestRepo(ArrayList<String> fields, boolean expectSuccess) {
		StringBuffer cmd = new StringBuffer("pulp-admin repo update ");
		Iterator itr = fields.iterator();
		while (itr.hasNext()) {
			cmd.append((String)itr.next()).append(" ");
		}

		if (expectSuccess) {
			Assert.assertEquals(sshCommandRunner.runCommandAndWait(cmd.toString()).getExitCode(), Integer.valueOf(0), "Updating repo info: ");
		}
		else {
			Assert.assertNotSame(sshCommandRunner.runCommandAndWait(cmd.toString()).getExitCode(), Integer.valueOf(0), "Updating repo info: ");
		}
		return sshCommandRunner.getStdout();
	}

	public String uploadContent(String repoId, String rpmName, String rpmUrl, String tmpRpmDir, boolean expectSuccess) {
		// TODO: Use fetchFile()
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("cd " + tmpRpmDir + " && wget -m -nd "+ rpmUrl).getExitCode(), Integer.valueOf(0), "Downloading rpm package to "+tmpRpmDir);
		if (expectSuccess) {
			if (repoId.equals("")) {
				Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin content upload -v "+tmpRpmDir+"/"+rpmName).getExitCode(), Integer.valueOf(0), "Uploading package to pulp without association to repo: ");
			}
			else {
				Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin content upload -v --repoid="+repoId+" "+tmpRpmDir+"/"+rpmName).getExitCode(), Integer.valueOf(0), "Uploading package to repo: ");
			}
		}
		else {
			if (repoId.equals("")) {
				//Assert.assertNotSame(sshCommandRunner.runCommandAndWait("pulp-admin content upload -v "+tmpRpmDir+"/"+rpmName).getExitCode(), Integer.valueOf(0), "Uploading package to pulp without association to repo: ");
				// Temporary workaround for content bz 752098
				sshCommandRunner.runCommandAndWait("pulp-admin content upload -v "+tmpRpmDir+"/"+rpmName);
			}
			else {
				//Assert.assertNotSame(sshCommandRunner.runCommandAndWait("pulp-admin content upload -v --repoid="+repoId+" "+tmpRpmDir+"/"+rpmName).getExitCode(), Integer.valueOf(0), "Uploading package to repo: ");
				// Temporary workaround for content bz 752098
				sshCommandRunner.runCommandAndWait("pulp-admin content upload -v --repoid="+repoId+" "+tmpRpmDir+"/"+rpmName);
			}
		}
		return sshCommandRunner.getStdout();
	}

	public void uploadUnsignedContent(String repoId, String rpmName, String rpmUrl, String tmpRpmDir) {
		// TODO: Use fetchFile()
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("cd " + tmpRpmDir + " && wget -m -nd "+ rpmUrl).getExitCode(), Integer.valueOf(0), "Downloading rpm package to "+tmpRpmDir);
		if (repoId.equals("")) {
			Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin content upload --nosig "+tmpRpmDir+"/"+rpmName).getExitCode(), Integer.valueOf(0), "Uploading package to pulp without association to repo: ");
		}
		else {
			Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin content upload --nosig --repoid="+repoId+" "+tmpRpmDir+"/"+rpmName).getExitCode(), Integer.valueOf(0), "Uploading package to repo: ");
		}
	}

	public String uploadChunkedContent(String fileName, String fileUrl, String tmpDir, String chunkSize, SSHCommandRunner subRunner) {
		String rtn = "";
		fetchFile(fileUrl, tmpDir);
		sshCommandRunner.setCommand("pulp-admin content upload --chunksize="+chunkSize + " " +tmpDir+"/"+fileName);
		sshCommandRunner.run();
		
		String result = "";
		while (true) {	
			result = subRunner.runCommandAndWait("cd /var/lib/pulp/uploads && find -iname \"*.dat\" -exec ls -lart {} \\;").getStdout();
			if (!result.equals("") && result.split("\n").length > 2) {
				break;
			}
			try {
				Thread.currentThread();
				Thread.sleep(5000);
			}
			catch (Exception e) {
			}
		}
		try {
			String[] chunks = result.split("\n");
			rtn = chunks[0].split(" ")[4];
		}
		catch (ArrayIndexOutOfBoundsException e) {
			e.printStackTrace();
		}
		// wait till the command is finally over and make sure it exits 0
		Assert.assertEquals(sshCommandRunner.waitFor(), Integer.valueOf(0), "Uploading " + fileName);
		return rtn;
	}

	public void deleteRPM(String repoId, String rpmName) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin repo delete_package --id="+repoId+" -p "+rpmName).getExitCode(), Integer.valueOf(0), "Deleting package from repo: ");
	}

	public ArrayList<String> findAllTestRpms(String tmpRpmDir) {
		ArrayList<String> rtn = new ArrayList<String>();
		String[] fileList = sshCommandRunner.runCommandAndWait("cd " + tmpRpmDir + " && ls *.rpm").getStdout().split("\n");
		for (String file : fileList) {
			rtn.add(file.trim());
		}
		return rtn;
	}

	public boolean doesLinkExist(String fullPath) {
		if (sshCommandRunner.runCommandAndWait("[ -h " + fullPath + " ] && exit 0 || exit 1").getExitCode() == 0) {
			return true;
		}
		else {
			return false;
		}
	}

	public boolean isFolderEmpty(String fullPath) {
		if (sshCommandRunner.runCommandAndWait("ls -l " + fullPath + "| wc -l").getStdout().equals("0")) {
			return true;
		}
		else {
		}
			return false;
	}
	
	public void packageList(String repoId, String queryName) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin package info --repoid="+repoId+" -n "+queryName).getExitCode(), Integer.valueOf(0), "Checking pkg info");
	}

	public void installGroupPackage(String pkgName, String consumerGroupId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin package install -n "+pkgName+" --consumergroupid=" + consumerGroupId, Long.valueOf(60000)).getExitCode(), Integer.valueOf(0), "Installing pkg on consumer "+consumerGroupId);
	}
	public void createConsumerGroup(String groupId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin consumergroup create --id "+groupId).getExitCode(), Integer.valueOf(0), "Creating consumer group ");
	}

	public void addConsumerToGroup(String consumerId, String groupId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin consumergroup add_consumer --id "+groupId + " --consumerid="+consumerId).getExitCode(), Integer.valueOf(0), "Adding consumer to group: ");
	}

	public void deleteConsumerGroup(String groupId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin consumergroup delete --id "+groupId).getExitCode(), Integer.valueOf(0), "Deleting consumer group ");
	}

	public void createPkgGroup(String pkgGroupName, String repoId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin packagegroup create --id "+pkgGroupName+" --repoid="+repoId+" --name="+pkgGroupName).getExitCode(), Integer.valueOf(0), "Creating pkg group ");
	}

	public void addToPkgGroup(String pkgGroupName, String repoId, String pkgName) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin packagegroup add_package --id "+pkgGroupName+" --repoid="+repoId+" --name="+pkgName).getExitCode(), Integer.valueOf(0), "Adding "+pkgName+" to "+pkgGroupName);
	}

	public void removePkgFromGroup(String pkgGroupName, String repoId, String pkgName) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin packagegroup delete_package --id "+pkgGroupName+" --repoid="+repoId+" --name="+pkgName).getExitCode(), Integer.valueOf(0), "Removing "+pkgName+" from "+pkgGroupName);
	}

	public String pkgGroupInfo(String pkgGroupName, String repoId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin packagegroup info --id "+pkgGroupName+" --repoid="+repoId).getExitCode(), Integer.valueOf(0), "Fetching info on "+pkgGroupName);
		return sshCommandRunner.getStdout();
	}

	public String listPkgGroup(String repoId, boolean expectSuccess) {
		if (expectSuccess) {
			Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin packagegroup list --repoid "+repoId).getExitCode(), Integer.valueOf(0), "Listing package groups on "+repoId);
		}
		else {
			Assert.assertNotSame(sshCommandRunner.runCommandAndWait("pulp-admin packagegroup list --repoid "+repoId).getExitCode(), Integer.valueOf(0), "Listing package groups on "+repoId);
		}
		return sshCommandRunner.getStdout();
	}

	public String deletePkgGroup(String pkgGroupName, String repoId, boolean expectSuccess) {
		if (expectSuccess) {
			Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin packagegroup delete --id="+pkgGroupName+" --repoid="+repoId).getExitCode(), Integer.valueOf(0), "Deleting "+pkgGroupName);
		}
		else {
			Assert.assertNotSame(sshCommandRunner.runCommandAndWait("pulp-admin packagegroup delete --id="+pkgGroupName+" --repoid="+repoId).getExitCode(), Integer.valueOf(0), "Deleting "+pkgGroupName);
		}
		return sshCommandRunner.getStdout();
	}

	public String installGroupErrata(String consumerGroupId, String errataId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin errata install --consumergroupid="+consumerGroupId+" -e "+errataId).getExitCode(), Integer.valueOf(0), "Installing "+errataId);
		return sshCommandRunner.getStdout();
	}

	public String getOrphanList() {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin content list --orphaned").getExitCode(), Integer.valueOf(0), "Fetching orphan package list.");
		return sshCommandRunner.getStdout();
	}

	public String getContentList(String repoId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin content list --repoid="+repoId).getExitCode(), Integer.valueOf(0), "Fetching content list.");
		return sshCommandRunner.getStdout();
	}

	public void deleteOrphanedContent(String pkgName, String csvFile) {
		if (pkgName.equals("") && (!csvFile.equals(""))) {
			Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin content delete --csv=" + csvFile).getExitCode(), Integer.valueOf(0), "Delete orphan file.");
		}
		else if ((!pkgName.equals("")) && csvFile.equals("")) {
			Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin content delete -f " + pkgName).getExitCode(), Integer.valueOf(0), "Delete orphan file.");
		}
	}

	public void addPackage(String repoId, String pkgName, String csvFile) {
		StringBuffer cmd = new StringBuffer("pulp-admin repo add_package --id=" + repoId);
		if (pkgName.equals("") && (!csvFile.equals(""))) {
			cmd.append(" --csv=" + csvFile);
		}
		else if ((!pkgName.equals("")) && csvFile.equals("")) {
			cmd.append(" -p " + pkgName);
		}
		Assert.assertEquals(sshCommandRunner.runCommandAndWait(cmd.toString()).getExitCode(), Integer.valueOf(0), "Adding pkg to repo: ");
	}

	public void deletePackage(String repoId, String pkgName, String csvFile) {
		StringBuffer cmd = new StringBuffer("pulp-admin repo remove_package --id=" + repoId);
		if (pkgName.equals("") && (!csvFile.equals(""))) {
			cmd.append(" --csv=" + csvFile);
		}
		else if ((!pkgName.equals("")) && csvFile.equals("")) {
			cmd.append(" -p " + pkgName);
		}
		Assert.assertEquals(sshCommandRunner.runCommandAndWait(cmd.toString()).getExitCode(), Integer.valueOf(0), "Adding pkg to repo: ");
	}

	public void addContent(String repoId, String fileName, String csvFile) {
		StringBuffer cmd = new StringBuffer("pulp-admin repo add_file --id=" + repoId);
		if (fileName.equals("") && (!csvFile.equals(""))) {
			cmd.append(" --csv=" + csvFile);
		}
		else if ((!fileName.equals("")) && csvFile.equals("")) {
			cmd.append(" -f " + fileName);
		}
		Assert.assertEquals(sshCommandRunner.runCommandAndWait(cmd.toString()).getExitCode(), Integer.valueOf(0), "Adding file to repo: ");
	}

	public void deleteContent(String repoId, String fileName, String csvFile) {
		StringBuffer cmd = new StringBuffer("pulp-admin repo remove_file --id=" + repoId);
		if (fileName.equals("") && (!csvFile.equals(""))) {
			cmd.append(" --csv=" + csvFile);
		}
		else if ((!fileName.equals("")) && csvFile.equals("")) {
			cmd.append(" -f " + fileName);
		}
		Assert.assertEquals(sshCommandRunner.runCommandAndWait(cmd.toString()).getExitCode(), Integer.valueOf(0), "Adding file to repo: ");
	}
	
	// TODO: The following 2 methods need to be refactored/abstracted out

	public void fetchFile(String fileURL, String tmpDir) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("wget -m -nd -P " + tmpDir + " " + fileURL).getExitCode(), Integer.valueOf(0), "Downloading " + fileURL + " to "+tmpDir);
	}

	public void fetchFile(String certPath, String certKeyPath, String caCertPath, String fileURL, String tmpDir) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("cd " + tmpDir + " && wget -N --certificate="+certPath + " --private-key=" + certKeyPath + " --ca-certificate=" + caCertPath +  " " + fileURL).getExitCode(), Integer.valueOf(0), "Downloading " + fileURL + " to "+tmpDir);
	}

	public void localFetchFile(String fileURL, String tmpDir) {
		try {
			String line;
			Runtime r = Runtime.getRuntime();
			Process p = r.exec("wget -m -nd -P " + tmpDir + " "+ fileURL);
			p.waitFor();
			BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
			while ((line = input.readLine()) != null) {
				System.out.println(line);
			}
			input.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void localFetchFile(String certPath, String certKeyPath, String caCertPath, String fileURL, String tmpDir) {
		try {
			String line;
			Runtime r = Runtime.getRuntime();
			Process p = r.exec("wget -m -nd " + "--certificate="+certPath + " --private-key=" + certKeyPath + " --ca-certificate=" + caCertPath +  " -P " + tmpDir + " "+ fileURL);
			p.waitFor();
			BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
			while ((line = input.readLine()) != null) {
				System.out.println(line);
			}
			input.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
/*
	public String getSegments() {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("cd /var/lib/pulp/uploads && find -iname \"*.dat\" -exec ls -lart {} \\;").getExitCode(), Integer.valueOf(0), "Fetching chunks data.");
		return sshCommandRunner.getStdout();
	}
	*/
	
	public void importPkgGroupViaComps(String repoId, String fileName, String fileURL, String tmpDir) {
		fetchFile(fileURL, tmpDir);
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin packagegroup import --repoid="+repoId+" --comps="+tmpDir+"/"+fileName).getExitCode(), Integer.valueOf(0), "Importing pkg group");
	}

	public void exportPkgGroupViaComps(String repoId, String fileName, String tmpDir) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin packagegroup export --repoid="+repoId+" --out="+tmpDir+"/"+fileName).getExitCode(), Integer.valueOf(0), "Exporting pkg group");
	}

	public ArrayList<Hashtable<String, String>> getAllGroupsFromCompXML(String fullFileName) {
		ArrayList<Hashtable<String, String>> rtn = new ArrayList<Hashtable<String, String>>();
		Object[] args = {fullFileName};
		for (Object groupInfo : (ArrayList)groovyObject.invokeMethod("getAllGroups", args)) {
			Hashtable<String, String> groupFields = new Hashtable<String, String>();
			for (String field : ((String)groupInfo).split(",")) {
				String key = field.split("=")[0];
				String val = field.split("=")[1];
				groupFields.put(key, val);
			}
			rtn.add(groupFields);
		}
		return rtn;
	}
	
	public String getPrimaryXMLFileName(String repomdFileName) {
		ArrayList<String> rtn = new ArrayList<String>();
		Object[] args = {repomdFileName};
		return (String)groovyObject.invokeMethod("findPrimaryXML", args);
	}

	public ArrayList<String> getAllPackagesFromPrimaryXML(String fullFileName) {
		ArrayList<String> rtn = new ArrayList<String>();
		Object[] args = {fullFileName};
		for (Object packageFileName : (ArrayList)groovyObject.invokeMethod("getAllPackages", args)) {
			rtn.add((String)packageFileName);
		}
		return rtn;
	}

	public boolean doesCompGroupExist(String file, String id, String description) {
		Object[] args1 = {file, id};
		Object[] args2 = {file, description};
		// TODO: Squash this in 1 call so it doesn't mix & match group's id and description
		if ((Boolean)groovyObject.invokeMethod("doesIdInGroupExist", args1) && (Boolean)groovyObject.invokeMethod("doesDescriptionInGroupExist", args2)) {
			return true;
		}
		return false;
	}

	public void registerCDS(String hostname) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin cds register --hostname " + hostname + " --name " + hostname).getExitCode(), Integer.valueOf(0), "Registering " + hostname + " to CDS.");
	}

	public void unregisterCDS(String hostname) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin cds unregister --hostname " + hostname).getExitCode(), Integer.valueOf(0), "Unregistering " + hostname + " from the CDS.");
	}

	public void associateRepoToCDS(String hostname, String repoId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin cds associate_repo --hostname " + hostname + " --repoid " + repoId).getExitCode(), Integer.valueOf(0), "Associating repo " + repoId + " to "  + hostname + " to CDS.");
	}

	public void unassociateRepoToCDS(String hostname, String repoId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin cds unassociate_repo --hostname " + hostname + " --repoid " + repoId).getExitCode(), Integer.valueOf(0), "Unassociating repo " + repoId + " to "  + hostname + " to CDS.");
	}

	public String getCDSHistory(String hostname, String opts, boolean expectedSuccess) {
		String cmd = "pulp-admin cds history --hostname " + hostname + " " + opts;
		if (expectedSuccess) {
			Assert.assertEquals(sshCommandRunner.runCommandAndWait(cmd).getExitCode(), Integer.valueOf(0), "Getting CDS history.");
		}
		else {
			Assert.assertNotSame(sshCommandRunner.runCommandAndWait(cmd).getExitCode(), Integer.valueOf(0), "Getting CDS history.");
		}
		return (sshCommandRunner.getStdout() + sshCommandRunner.getStderr());
	}

	public String execCmd(String cmd) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait(cmd).getExitCode(), Integer.valueOf(0));
		return (sshCommandRunner.getStdout() + sshCommandRunner.getStderr());
	}
	
	public void generateMetadata(String repoId) {
			Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin repo generate_metadata --id=" + repoId).getExitCode(), Integer.valueOf(0), "Generating repo metadata.");
	}

	public String getMetadataStatus(String repoId) {
			sshCommandRunner.runCommandAndWait("pulp-admin repo generate_metadata --status --id=" + repoId);
			return sshCommandRunner.getStdout()+sshCommandRunner.getStderr();
	}
}
