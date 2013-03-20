package com.redhat.qe.pulp.api.base;

import java.io.IOException;
import java.text.ParseException;

import org.testng.annotations.BeforeSuite;

import com.redhat.qe.auto.testng.TestScript;
import com.redhat.qe.tools.SSHCommandRunner;

import com.redhat.qe.pulp.api.tasks.PulpTasks;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

public class PulpTestScript extends TestScript {
	protected String serverHostname			= System.getProperty("pulp.server.hostname");
	protected String clientHostname			= System.getProperty("pulp.client.hostname");
	protected String username				= System.getProperty("pulp.api.username", "admin");
	protected String password	 			= System.getProperty("pulp.api.password", "admin");

	protected String sshUser				= System.getProperty("pulp.ssh.user","root");
	protected String sshPassphrase			= System.getProperty("pulp.ssh.passphrase","");
	protected String sshKeyPrivate			= System.getProperty("pulp.sshkey.private",".ssh/id_auto_dsa");
	protected String sshKeyPassphrase		= System.getProperty("pulp.sshkey.passphrase","");

	protected String reinstall_flag			= System.getProperty("pulp.reinstall");

	public static SSHCommandRunner server	= null;
	public static SSHCommandRunner client	= null;

	protected static PulpTasks servertasks = null;

	public PulpTestScript() {
		super();
	

		try {
			client = new SSHCommandRunner(clientHostname, sshUser, sshPassphrase, sshKeyPrivate, sshKeyPassphrase, null);
			server = new SSHCommandRunner(serverHostname, sshUser, sshPassphrase, sshKeyPrivate, sshKeyPassphrase, null);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		servertasks = new PulpTasks(server, serverHostname, username, password);
	}	

	@BeforeSuite(groups="setup", description="setup pulp box")
	public void setup() throws ParseException, IOException {
		if (Boolean.parseBoolean(reinstall_flag)) {
			client.runCommandAndWait("yum remove -y pulp* grinder* gofer* && yum clean all");
			server.runCommandAndWait("yum remove -y pulp* grinder* gofer* && yum clean all");

			client.runCommandAndWait("rm -vfr /var/lib/pulp");
			server.runCommandAndWait("rm -vfr /var/lib/pulp");

			client.runCommandAndWait("yum install -y pulp gofer");
			server.runCommandAndWait("yum install -y pulp gofer");

			client.runCommandAndWait("sed -i s/host\\ =\\ localhost\\.localdomain/host\\ =\\ "+serverHostname+"/g /etc/pulp/client.conf");
			client.runCommandAndWait("sed -i s/localhost/"+serverHostname+"/g /etc/pulp/client.conf");
			server.runCommandAndWait("sed -i s/host\\ =\\ localhost\\.localdomain/host\\ =\\ $HOSTNAME/g /etc/pulp/client.conf");
			client.runCommandAndWait("sed -i s/localhost/"+serverHostname+"/g /etc/gofer/agent.conf");

			server.runCommandAndWait("sed -i s/localhost/$HOSTNAME/g /etc/pulp/client.conf");
			server.runCommandAndWait("sed -i s/localhost/$HOSTNAME/g /etc/pulp/pulp.conf");
			server.runCommandAndWait("sed -i s/localhost/$HOSTNAME/g /etc/gofer/agent.conf");

			client.runCommandAndWait("service mongod restart && service goferd restart && service qpidd restart && service httpd restart");
			server.runCommandAndWait("service mongod restart && service goferd restart && service qpidd restart && service httpd restart");

			server.runCommandAndWait("pulp-migrate");
		}
	}
	
	public JSONObject JSONifyString(String stuff) throws JSONException{
			return new JSONObject(stuff);
	}

	public JSONArray JSONifyArray(String stuff) throws JSONException{
			return new JSONArray(stuff);
	}
}
