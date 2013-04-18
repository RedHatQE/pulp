package com.redhat.qe.pulp.v2_cli.base;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Hashtable;
import java.util.Map;

import java.io.IOException;
import java.text.ParseException;

import org.testng.annotations.Test;
import com.redhat.qe.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.DataProvider;

import com.redhat.qe.pulp.abstraction.PulpAbstraction;
import com.redhat.qe.tools.SSHCommandRunner;
import com.redhat.qe.auto.testng.TestNGUtils;

import com.redhat.qe.pulp.v2_cli.tasks.PulpTasks;

public class PulpTestScript extends com.redhat.qe.auto.testng.TestScript {
	protected String serverHostname;

	protected String clientHostname;

	protected String sshUser;
	protected String sshPassphrase;
	protected String sshKeyPrivate;
	protected String sshKeyPassphrase;
	
	protected String consumerId;
	protected String consumerGroupId;

	protected String tmpRpmDir;

	protected String reinstall_flag;

	public static SSHCommandRunner server	= null;
	public static SSHCommandRunner client	= null;

	protected static PulpTasks servertasks	= null;
	protected static PulpTasks clienttasks	= null;

	protected PulpAbstraction pulpAbs	= null;

	public PulpTestScript() {
		super();

		serverHostname = System.getProperty("pulp.server.hostname");
		clientHostname = System.getProperty("pulp.client.hostname");
		sshUser = System.getProperty("pulp.ssh.user", "root");
		sshPassphrase = System.getProperty("pulp.ssh.passphrase", "");
		sshKeyPrivate = System.getProperty("pulp.sshkey.private", ".ssh/id_auto_dsa");
		sshKeyPassphrase = System.getProperty("pulp.sshkey.passphrase", "");
		tmpRpmDir = System.getProperty("pulp.tmpRpmDir");
		reinstall_flag = System.getProperty("pulp.reinstall");
		consumerId	= "test_consumer";
		consumerGroupId = "test_consumer_group";

		pulpAbs = new PulpAbstraction();	
	
		try {
			client = new SSHCommandRunner(clientHostname, sshUser, sshPassphrase, sshKeyPrivate, sshKeyPassphrase, null);
			server = new SSHCommandRunner(serverHostname, sshUser, sshPassphrase, sshKeyPrivate, sshKeyPassphrase, null);
			clienttasks = new PulpTasks(client);
			servertasks = new PulpTasks(server);
		} 
		catch (Exception e) {
			e.printStackTrace();
		}

		Hashtable<String, String> rules = new Hashtable<String, String>();	
		rules.put("NoRepo", "^No repos available.*$");
		rules.put("headerStart", "^[\\+\\-]+$");
		rules.put("repoHeader", "^[\\s]*List of Available Repositories[\\s]*$");
		rules.put("headerEnd", "^[\\+\\-]+$");
		rules.put("consumerHeader", "^[\\s]*Consumers[\\s]*$");
		rules.put("consumerGroupHeader1", "^[\\s]*List of Available Consumer Groups[\\s]*$");

		rules.put("consumerId", "^Id:[\\s]+([a-zA-Z0-9\\_]+)[\\s]*$");
		rules.put("consumerDescription", "^Description:[\\s]+([a-zA-Z0-9\\_]+)[\\s]*$");

		rules.put("consumerGroupId", "^Id[\\s]+([a-zA-Z0-9\\_]+)[\\s]*$");
		rules.put("consumerGroupDescription", "^Description[\\s]+([a-zA-Z0-9\\_]+)[\\s]*$");
		rules.put("consumerGroupMembers", "^Consumer ids[\\s]+([a-zA-Z0-9\\_\\/\\[\\]]+)[\\s]*$");
		rules.put("consumerAdditionalInfo", "^Additional info[\\s]+([a-zA-Z0-9\\_]+)[\\s]*$");

		rules.put("repoId", "^Id:[\\s]+([a-zA-Z0-9\\_]+)[\\s]*$");
		rules.put("repoName", "^Display Name:[\\s]+([a-zA-Z0-9\\_]+)[\\s]*$");
		rules.put("repoLocation", "^[\\s]+Feed URL:[\\s]+(.*)[\\s]*$");
		rules.put("repoType", "^Feed Type:[\\s]+([a-zA-Z]*)[\\s]*$");
		rules.put("repoArch", "^Architecture:[\\s]+([a-zA-Z0-9\\_]+)[\\s]*$");
		rules.put("repoSyncSchedule", "^Sync Schedule:[\\s]+([a-zA-Z0-9\\_]+)[\\s]*$");
		rules.put("repoPackages", "^Packages:[\\s]+([a-zA-Z0-9\\_\\/]+)[\\s]*$");
		rules.put("repoClones", "^Clones:[\\s]+([a-zA-Z0-9\\_\\/\\,\\[\\]\\']+)[\\s]*$");

		pulpAbs.appendRegexCriterion(rules);
	}

	@BeforeSuite(groups="setup", description="setup pulp box")
	public void setup() throws ParseException, IOException {
		//TODO: We know this is the same box, so run it once
		if (Boolean.parseBoolean(reinstall_flag)) {
			client.runCommandAndWait("yum remove -y pulp* python-pulp* grinder* gofer* && yum clean all");
			server.runCommandAndWait("yum remove -y pulp* python-pulp* grinder* gofer* && yum clean all");
			client.runCommandAndWait("yum update -y");
			server.runCommandAndWait("yum update -y");

			client.runCommandAndWait("rm -fr /var/lib/pulp");
			server.runCommandAndWait("rm -fr /var/lib/pulp");

			client.runCommandAndWait("yum install -y --nogpg pulp* gofer");
			server.runCommandAndWait("yum install -y --nogpg pulp* gofer");

			client.runCommandAndWait("sed -i s/host\\ =\\ localhost\\.localdomain/host\\ =\\ "+serverHostname+"/g /etc/pulp/consumer/consumer.conf");
			client.runCommandAndWait("sed -i s/host\\ =\\ localhost\\.localdomain/host\\ =\\ "+serverHostname+"/g /etc/pulp/admin/admin.conf");
			client.runCommandAndWait("sed -i s/localhost/"+serverHostname+"/g /etc/pulp/admin/admin.conf");
			client.runCommandAndWait("sed -i s/localhost/"+serverHostname+"/g /etc/pulp/pulp.conf");
			server.runCommandAndWait("sed -i s/host\\ =\\ localhost\\.localdomain/host\\ =\\ $HOSTNAME/g /etc/pulp/consumer/consumer.conf");
			server.runCommandAndWait("sed -i s/host\\ =\\ localhost\\.localdomain/host\\ =\\ $HOSTNAME/g /etc/pulp/admin/admin.conf");
			server.runCommandAndWait("sed -i s/host\\ =\\ localhost\\.localdomain/host\\ =\\ $HOSTNAME/g /etc/pulp/admin/v2_admin.conf");
			server.runCommandAndWait("sed -i s/wrap_width\\ =\\ 80/wrap_width\\ =\\ 120/g /etc/pulp/admin/admin.conf");
			client.runCommandAndWait("sed -i s/localhost/"+serverHostname+"/g /etc/pulp/consumer/consumer.conf");
			client.runCommandAndWait("sed -i s/localhost/"+serverHostname+"/g /etc/gofer/agent.conf");

			server.runCommandAndWait("sed -i s/localhost/$HOSTNAME/g /etc/pulp/server.conf");
			server.runCommandAndWait("sed -i s/localhost/$HOSTNAME/g /etc/pulp/consumer/consumer.conf");
			server.runCommandAndWait("sed -i s/localhost/$HOSTNAME/g /etc/pulp/admin/admin.conf");
			server.runCommandAndWait("sed -i s/localhost/$HOSTNAME/g /etc/pulp/admin/v2_admin.conf:host");
			server.runCommandAndWait("sed -i s/localhost/$HOSTNAME/g /etc/gofer/agent.conf");

			server.runCommandAndWait("service mongod restart && service goferd restart && service qpidd restart && service httpd restart");
			server.runCommandAndWait("pulp-manage-db");
			server.runCommandAndWait("service httpd restart");
			client.runCommandAndWait("service mongod restart && service goferd restart && service qpidd restart && service httpd restart");
		}
	}

	@BeforeSuite(groups={"setup"}, description="login w/ known credentials",
		dependsOnMethods = {"setup"})
	public void login() throws ParseException, IOException {
		clienttasks.login();
		servertasks.login();
	}

	@BeforeSuite(groups={"setup"}, description="create test consumers",
		dependsOnMethods = {"login"})
	public void createConsumer() throws ParseException, IOException {
		for (List<Object> c : getConsumerData()) {
			PulpTasks task = (PulpTasks)c.get(1);
			String c_id = (String)c.get(0);
			task.createConsumer(c_id, true);
		}
	}

	@BeforeSuite(description="pulp-cli: list the consumers, check if consumer has been properly added", 
		groups={"setup"}, dependsOnMethods = {"createConsumer"})
	public void createConsumerListTest() {
		ArrayList<Hashtable<String, String>> filteredResult = pulpAbs.match(servertasks.listConsumer());
		for (List<Object> consumer : getConsumerData()) {
			String consumerId = (String)consumer.get(0);
			PulpTasks task = (PulpTasks)consumer.get(1);
			String result = task.listConsumer();
			Assert.assertTrue(result.contains(consumerId), "Checking if " + consumerId + " is registered.");
		}
	}

/*
	@BeforeSuite(groups={"setup"}, description="create test consumer group",
		dependsOnMethods={"createConsumerListTest"})
	public void createConsumerGroup() {
		String consumerGroupId = "";
		for (List<Object> consumerGrp : getConsumerGroupData()) {
			consumerGroupId = (String)consumerGrp.get(0);
			servertasks.createConsumerGroup(consumerGroupId);
		}
		for (List<Object> consumer : getConsumerData()) {
			String consumerId = (String)consumer.get(0);
			servertasks.addConsumerToGroup(consumerId, consumerGroupId);
		}
	}

	@BeforeSuite(groups={"setup"}, description="pulp-cli: list the consumers in the group, check if test consumers has been added properly.",
		dependsOnMethods={"createConsumerGroup"})
	public void consumerGroupListTest() {
		for (List<Object> consumerGrp : getConsumerGroupData()) {
			String consumerGroupId = (String)consumerGrp.get(0);
			ArrayList<Hashtable<String, String>> filteredResult = pulpAbs.match(servertasks.listConsumerGroup());
		}
	}
*/

	@AfterSuite(groups={"teardown"}, alwaysRun=true, description="pulp teardown part 1.1")
	public void deleteConsumer() throws ParseException, IOException {
		for (List<Object> consumer : getConsumerData()) {
			String consumerId = (String)consumer.get(0);
			PulpTasks task = (PulpTasks)consumer.get(1);
			task.deleteConsumer(consumerId);
		}
	}
/*

	@AfterSuite(groups={"teardown"}, alwaysRun=true, description="pulp teardown part 1.2", 
		dependsOnMethods = {"deleteConsumer"})
	public void deleteConsumerGroup() throws ParseException, IOException {
		for (List<Object> consumerGrp : getConsumerGroupData()) {
			String consumerGroupId = (String)consumerGrp.get(0);
			servertasks.deleteConsumerGroup(consumerGroupId);
		}
	}
*/

	@AfterSuite(description="pulp-cli: list the consumers, check if consumer has been properly deleted", 
		groups={"teardown"}, alwaysRun=true, dependsOnMethods={"deleteConsumer"})
	public void deleteConsumerListTest() {
		for (List<Object> consumer : getConsumerData()) {
			String consumerId = (String)consumer.get(0);
			PulpTasks task = (PulpTasks)consumer.get(1);

			ArrayList<Hashtable<String, ArrayList<String>>> filteredResult = this.match(task.listConsumer(), ":");
			Hashtable<String, ArrayList<String>> result = pulpAbs.findConsumer(filteredResult, consumerId);
			Assert.assertNull(result.get("consumerId"), "comparing consumerId to null.");
			Assert.assertNull(result.get("consumerDescription"), "comparing consumerDescription to null.");
			Assert.assertNull(result.get("consumerSubscription"), "comparing consumerSubscription to null.");
			Assert.assertNull(result.get("consumerPackage"), "comparing consumerPacakge to null.");
		}
	}
/*
	
	@AfterSuite(description="pulp-cli: list the consumers, check if consumer has been properly deleted", 
			groups={"teardown"}, alwaysRun=true, dependsOnMethods={"deleteConsumerGroup"})
	public void deleteConsumerGroupListTest() {
		for (List<Object> consumerGrp : getConsumerGroupData()) {
			String consumerGroupId = (String)consumerGrp.get(0);
	
			ArrayList<Hashtable<String, String>> filteredResult = pulpAbs.match(servertasks.listConsumerGroup());
			Hashtable<String, String> result = pulpAbs.findConsumer(filteredResult, consumerGroupId);
			Assert.assertNull(result.get("consumerGroupId"), "comparing consumerGroupId to null.");
			Assert.assertNull(result.get("consumerGroupDescription"), "comparing consumerGroupDescription to null.");
		}
	}
*/

	@AfterSuite(groups={"teardown"}, description="pulp teardown last part", 
		alwaysRun=true, dependsOnMethods = {"deleteConsumerListTest"})
	public void logout() throws ParseException, IOException {
		clienttasks.logout();
		servertasks.logout();
	}

	// Utility Methods
	// ====================================================================
	public int getYumPkgCount(String repoList, String repoName) {
		String[] repos = repoList.split("\n");
		for (String r : repos) {
			String repoline = r.replaceAll("[\\s]+", " ");
			String[] repoFields = repoline.split(" ");
			String repo_name = repoFields[0];
			if (repo_name.equals(repoName)) {
				String pkg_count = repoFields[repoFields.length - 1];
				return Integer.parseInt(pkg_count.replace(",", ""));
			}
		}
		return 0;
	}

	public String[] decodePkg(String result) {
		String[] rtn = new String[0];
		// [u'patb', u'emoticons']
		try {
			// TODO: Temporary fix until we can parse CLI output correctly
			// result > result.split("\n")[0]
			rtn = result.split("\n")[0].replaceAll("[\\[\\]]", "").replaceAll("u\\'", "").replaceAll("[\\'\\s]", "").split(",");
		} catch (Exception e) {
			log.warning("Cannot decode stdout pkg string");
			e.printStackTrace();
		}
		return rtn;
	}

	public void uninstallPkg(String pkgName, PulpTasks task) {
		// finish off all transactions
		while (true) {
			String tresult = task.pollTransactions();
			if (tresult.trim().length() == 0) {
				break;
			}
		}
		// make sure pkg is installed first
		try {
			Thread.currentThread();
			Thread.sleep(5000);
		} catch (Exception e) {};
		if (task.packageSearch(pkgName).contains(pkgName)) {
			task.uninstallPkg(pkgName);
		}
		// verify
		String result = task.packageSearch(pkgName);
		Assert.assertEquals(result, "", "Verifying the package has been wiped correctly");
	}

	public void groupUninstallPkg(String pkgName) {
		// wait for all yum transactions to finish 
		while (true) {
			String stresult = servertasks.pollTransactions();
			String ctresult = clienttasks.pollTransactions();
			if (stresult.trim().length() == 0 && ctresult.trim().length() == 0) 			{
				{
					break;
				}
			}
			// make sure pkg is installed first
			if (servertasks.packageSearch(pkgName).contains(pkgName)) {
				servertasks.uninstallPkg(pkgName);
			}

			// make sure pkg is installed first
			if (clienttasks.packageSearch(pkgName).contains(pkgName)) {
				clienttasks.uninstallPkg(pkgName);
			}

			// verify
			String result = servertasks.packageSearch(pkgName);
			Assert.assertEquals(result, "", "Verifying the package has been wiped correctly");

			// verify
			result = clienttasks.packageSearch(pkgName);
			Assert.assertEquals(result, "", "Verifying the package has been wiped correctly");
		}
	}

	public boolean testThroughOptions(String cmd, Hashtable<String, String> opts, Hashtable<String, String> expectedResults) {
		// Note: Ideally, we'll do a powerset and get all of the combo. Next iter?
		boolean rtn = true;
		String cmdResult = servertasks.execCmd(cmd);
		Iterator it = opts.entrySet().iterator();
		for (String line : cmdResult.split("\n")) {
			while (it.hasNext()) {
				Map.Entry<String, String> entry = (Map.Entry)it.next();
				if (line.trim().indexOf(entry.getKey()) != -1) {
					String c = cmd.replace("--help", "") + " " + entry.getKey() + "='" + entry.getValue() + "'";
					String r = servertasks.execCmd(c);
					Assert.assertTrue(r.contains(expectedResults.get(entry.getKey())));
				}
			}
		}
		return rtn;
	}

	public ArrayList<Hashtable<String, ArrayList<String>>> match(String input, String divider) {
		ArrayList<Hashtable<String, ArrayList<String>>> rtn = new ArrayList<Hashtable<String, ArrayList<String>>>();
		
		// preprocess
		ArrayList<Integer> counter = new ArrayList<Integer>();
		String[] iter = input.split("\n");
		for (int i=0; i < iter.length; i++) {
			String line = iter[i];
			if (line.trim().equals("")) {
				counter.add(new Integer(i));
			}
		}
		
		ArrayList<String[]> pieces = new ArrayList<String[]>();
		try {
			if (counter.size() == 0) {
				// just one big chunk
				pieces.add(iter);
			} else {
				for (int j=1; j < counter.size(); j++) {
					int start = counter.get(j-1);
					int end = counter.get(j);
					pieces.add(Arrays.copyOfRange(iter, start, end));
				}
				// last piece
				int start = counter.get(counter.size()-1);
				int end = iter.length-1;
				pieces.add(Arrays.copyOfRange(iter, start, end));
			}
		} catch (ArrayIndexOutOfBoundsException aiobe) {
		}

		for (String[] p : pieces) {
			Hashtable<String, ArrayList<String>> entry = new Hashtable<String, ArrayList<String>>();
			for (int k=0; k < p.length; k++) {
				String l = p[k];
				if (l.contains(divider)) {
					String[] tmp = l.split(divider, 2);
					if (!tmp[1].trim().equals("")) {
						// check if key exists
						if (entry.containsKey(tmp[0].trim())) {
							ArrayList<String> e = entry.get(tmp[0].trim());
							e.add(tmp[1].trim());
						} else {
							ArrayList<String> e = new ArrayList<String>();
							e.add(tmp[1].trim());
							entry.put(tmp[0].trim(), e);
						}
					}
				}
			}
			rtn.add(entry);
		}

		return rtn;
	}

	// ====================================================================
	@DataProvider(name="pkgData")
	public Object[][] pkgData() {
		return TestNGUtils.convertListOfListsTo2dArray(getPkgData());
	}
	public List<List<Object>> getPkgData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		data.add(Arrays.asList(new Object[]{"patb-0.1-1.noarch.rpm", "http://qeblade20.rhq.lab.eng.bos.redhat.com/pub/patb-0.1-1.noarch.rpm", tmpRpmDir, "patb"}));
		data.add(Arrays.asList(new Object[]{"emoticons-0.1-1.noarch.rpm", "http://qeblade20.rhq.lab.eng.bos.redhat.com/pub/emoticons-0.1-1.noarch.rpm", tmpRpmDir, "emoticons"}));
		return data;
	}

	@DataProvider(name="consumerData")
	public Object[][] consumerData() {
		return TestNGUtils.convertListOfListsTo2dArray(getConsumerData());
	}
	public List<List<Object>> getConsumerData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		data.add(Arrays.asList(new Object[]{consumerId, servertasks}));
		data.add(Arrays.asList(new Object[]{consumerId + "_remote", clienttasks}));
		return data;
	}

	@DataProvider(name="consumerGroupData")
	public Object[][] consumerGroupData() {
		return TestNGUtils.convertListOfListsTo2dArray(getConsumerGroupData());
	}
	public List<List<Object>> getConsumerGroupData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();
		data.add(Arrays.asList(new Object[]{consumerGroupId, servertasks}));
		return data;
	}
}
