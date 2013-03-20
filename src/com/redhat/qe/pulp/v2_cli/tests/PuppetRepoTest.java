package com.redhat.qe.pulp.v2_cli.tests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.testng.annotations.Test;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import com.redhat.qe.auto.testng.TestNGUtils;
import com.redhat.qe.Assert;
import org.testng.annotations.DataProvider;

import com.redhat.qe.pulp.v2_cli.tasks.PulpTasks;
import com.redhat.qe.pulp.v2_cli.base.PulpTestScript;

import com.redhat.qe.pulp.abstraction.PulpAbstraction;
import com.redhat.qe.tools.SSHCommandRunner;

public class PuppetRepoTest extends PulpTestScript {
	public PuppetRepoTest() {
		super();
	}

	@Test (groups={"testPuppetRepo"}, dataProvider="localRepoData")
	public void createTestRepo(ArrayList<String> fields) {
		servertasks.createTestRepo(fields);
	}

	// ###########################################################################
	@DataProvider(name = "localRepoData")
	public Object[][] localRepoData() {
		return TestNGUtils.convertListOfListsTo2dArray(getLocalRepoData());
	}
	public List<List<Object>> getLocalRepoData() {
		ArrayList<List<Object>> data = new ArrayList<List<Object>>();

		ArrayList<String> repoOpts = new ArrayList<String>();
		repoOpts.add("--repo-id=faux_test_repo");
		repoOpts.add("--feed=http://download.devel.redhat.com/released/F-13/GOLD/Fedora/x86_64/os");
		repoOpts.add("");
		data.add(Arrays.asList(new Object[]{repoOpts, true}));

		repoOpts = new ArrayList<String>();
		repoOpts.add("--repo-id=relative_test_repo");
		repoOpts.add("--feed=http://qeblade20.rhq.lab.eng.bos.redhat.com/pub/updates/");
		repoOpts.add("--relative-url="+relativeRepoPath);
		data.add(Arrays.asList(new Object[]{repoOpts, false}));

		repoOpts = new ArrayList<String>();
		repoOpts.add("--repo-id=upload_test_repo");
		repoOpts.add(""); // don't remove this as we're using this field to determine whether we're syncing the repo or not
		repoOpts.add(""); // ^^^
		data.add(Arrays.asList(new Object[]{repoOpts, false}));

		return data;
	}
}
