package com.redhat.qe.pulp.api.tasks;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.ArrayList;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import com.redhat.qe.tools.SSLCertificateTruster;

import com.redhat.qe.Assert;
import com.redhat.qe.tools.SSHCommandRunner;

public class PulpTasks {
	protected static Logger log = Logger.getLogger(PulpTasks.class.getName());
	protected SSHCommandRunner sshCommandRunner = null;
	private static HttpClient client;
	private String baseURL = null;
	private String cmdPrefix = null;
	private String username;
	private String password;

	public PulpTasks(SSHCommandRunner runner, String hostname, String username, String password) {
		setSSHCommandRunner(runner);		

		MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
		client = new HttpClient(connectionManager);
      	client.getParams().setAuthenticationPreemptive(true);
		
		try {
			SSLCertificateTruster.trustAllCertsForApacheHttp();
		}
		catch (Exception e) {
			log.log(Level.SEVERE, "Failed to trust all certificates for Apache HTTP Client", e);
		}

		baseURL = "https://" + hostname + "/pulp/api/";
		// hardcode username/password for now, might need to back out for cert?
		cmdPrefix = "curl -k -u \"" + username + ":" + password + "\" "; 
		this.username = username;
		this.password = password;
	}

	public void setSSHCommandRunner(SSHCommandRunner runner) {
		sshCommandRunner = runner;
	}

	public String createURL(String functionality, String call) {
		String rtn = baseURL + functionality + "/" + call + "/";
		return rtn;
	}
	
	public String executeGET(String functionality, String call, int expectedResponseCode) {
		String rtn = null;
		String url = createURL(functionality, call);
		GetMethod get = new GetMethod(url);
		try {
			log.info("curl alternative: " + cmdPrefix + url);
			HttpMethod m = doHTTPRequest(client, get, username, password, expectedResponseCode);
			rtn = m.getResponseBodyAsString();
			m.releaseConnection();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return rtn;
	}

	public String executePUT(String functionality, String call, int expectedResponseCode) {
		String rtn = null;
		String url = createURL(functionality, call);
		PutMethod put = new PutMethod(url);
		try {
			log.info("curl alternative: " + cmdPrefix + "--request PUT " + url);
			HttpMethod m = doHTTPRequest(client, put, username, password, expectedResponseCode);
			rtn = m.getResponseBodyAsString();
			m.releaseConnection();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return rtn;
	}

	public String executeDELETE(String functionality, String call, int expectedResponseCode) {
		String rtn = null;
		String url = createURL(functionality, call);
		DeleteMethod delete = new DeleteMethod(url);
		try {
			log.info("curl alternative: " + cmdPrefix + "--request DELETE " + url);
			HttpMethod m = doHTTPRequest(client, delete, username, password, expectedResponseCode);
			rtn = m.getResponseBodyAsString();
			m.releaseConnection();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return rtn;
	}

	public String executePOST(String functionality, String call, String requestBody, int expectedResponseCode) {
		String rtn = null;
		String url = createURL(functionality, call);
		PostMethod post = new PostMethod(url);

		try {
			if (requestBody != null) {
				post.setRequestEntity(new StringRequestEntity(requestBody, "application/json", null));
				post.addRequestHeader("accept", "application/json");
				post.addRequestHeader("content-type", "application/json");
			}

			// ===========For Generating curl alternative=============
			String data = requestBody==null? "":"--data '"+requestBody+"'";
			String headers = "";
			for ( org.apache.commons.httpclient.Header header : post.getRequestHeaders()) {
				headers+= "--header '"+header.toString().trim()+"' ";
			}
			// =======================================================

			log.info("curl alternative: " + cmdPrefix + "--request POST " + data + " " + headers + " " + url);
			HttpMethod m = doHTTPRequest(client, post, username, password, expectedResponseCode);
			rtn = m.getResponseBodyAsString();
			m.releaseConnection();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return rtn;
	}

	private HttpMethod doHTTPRequest(HttpClient client, HttpMethod method, String username, String password, int expectedResponseCode) throws Exception {
		String server = method.getURI().getHost();
		int port = method.getURI().getPort();
	
		setCredentials(client, server, port, username, password);
		log.finer("Running HTTP request: " + method.getName() + " on " + method.getURI() + " with credentials for '"+username+"' on server '"+server+"'...");
		if (method instanceof PostMethod){
			RequestEntity entity =  ((PostMethod)method).getRequestEntity();
			log.finer("HTTP Request entity: " + ((StringRequestEntity)entity).getContent());
		}
		// Comment out because of warning: non-varargs call of varargs method with inexact argument type for last parameter;
		//log.finer("HTTP Request Headers: " + TestHelper.interpose(", ", method.getRequestHeaders()));
		int responseCode = client.executeMethod(method);
		log.finer("HTTP server returned: " + responseCode);
		Assert.assertEquals(responseCode, expectedResponseCode, "Making sure response code matches expected results.");
		return method;
	}

	private void setCredentials(HttpClient client, String server, int port, String username, String password) {
		if (!username.equals(""))
			client.getState().setCredentials(
	            new AuthScope(server, port, AuthScope.ANY_REALM),
	            new UsernamePasswordCredentials(username, password)
	        );
	}

	// ==================================================================
	// Utility methods
	// ==================================================================
	public ArrayList<String> findAllTestRpms(String tmpRpmDir) {
		ArrayList<String> rtn = new ArrayList<String>();
		String[] fileList = sshCommandRunner.runCommandAndWait("cd " + tmpRpmDir + " && ls *.rpm").getStdout().split("\n");
		for (String file : fileList) {
			rtn.add(file.trim());
		}
		return rtn;
	}

	public boolean verifySyncStopped() {
		boolean rtn = false;
		String one = sshCommandRunner.runCommandAndWait("du --max-depth=1 /var/lib/pulp/ | grep \"repos\" | cut -f 1").getStdout().trim();
		try {
			Thread.currentThread().sleep(10000);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		String two = sshCommandRunner.runCommandAndWait("du --max-depth=1 /var/lib/pulp/ | grep \"repos\" | cut -f 1").getStdout().trim();

		rtn = one.equals(two);
		return rtn;
	}
}
