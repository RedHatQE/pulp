package com.redhat.qe.pulp.perf.tasks;

import java.util.logging.Logger;

import java.util.Hashtable;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.Iterator;

import com.redhat.qe.Assert;
import com.redhat.qe.tools.SSHCommandRunner;

import com.redhat.qe.pulp.cli.tasks.PulpTasks;

public class PerfPulpTasks extends PulpTasks{
	public PerfPulpTasks(SSHCommandRunner runner) {
		super(runner);
	}
/*
	public String createConsumer(String consumerId, boolean expectSuccess) {
		if (expectSuccess) {
			Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-client -u admin -p admin consumer create --id "+consumerId).getExitCode(), Integer.valueOf(0), "Creating consumer: ");
		}
		else {
			Assert.assertNotSame(sshCommandRunner.runCommandAndWait("pulp-client -u admin -p admin consumer create --id "+consumerId).getExitCode(), Integer.valueOf(0), "Creating consumer: ");
		}
		return sshCommandRunner.getStdout();
	}

	public String clientDeleteConsumer(boolean expectSuccess) {
		if (expectSuccess) {
			Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-client consumer delete").getExitCode(), Integer.valueOf(0), "Deleting consumer: ");
		}
		else {
			Assert.assertNotSame(sshCommandRunner.runCommandAndWait("pulp-client consumer delete").getExitCode(), Integer.valueOf(0), "Deleting consumer: ");
		}
		return sshCommandRunner.getStderr();
	}

	public void deleteConsumer(String consumerId) {
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("pulp-admin consumer delete --id=" + consumerId).getExitCode(), Integer.valueOf(0), "Deleting consumer: ");
	}
*/
}
