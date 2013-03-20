package com.redhat.qe.pulp.abstraction;

import java.util.Hashtable;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Logger;

import com.redhat.qe.tools.abstraction.CLIAbstraction;

public class PulpAbstraction extends CLIAbstraction{
	protected static Logger log = Logger.getLogger(PulpAbstraction.class.getName());
	public PulpAbstraction() {
		super();
	}

	public Hashtable<String, ArrayList<String>> findConsumer(ArrayList<Hashtable<String, ArrayList<String>>> matchedResult, String consumerId) {
		Hashtable<String, ArrayList<String>> rtn = new Hashtable<String, ArrayList<String>>();
		for (Hashtable<String, ArrayList<String>> result : matchedResult) {
			if (result.containsKey("Id") && consumerId.equals(result.get("Id").get(0))) {
				rtn = result;
				return rtn;
			}
		}
		return rtn;
	}

	public Hashtable<String, ArrayList<String>> findRepo(ArrayList<Hashtable<String, ArrayList<String>>> matchedResult, String repoId) {
		return findRepo(matchedResult, repoId, "Id");
	}

	public Hashtable<String, ArrayList<String>> findRepo(ArrayList<Hashtable<String, ArrayList<String>>> matchedResult, String repoId, String idIdentifier) {
		Hashtable<String, ArrayList<String>> rtn = new Hashtable<String, ArrayList<String>>();
		for (Hashtable<String, ArrayList<String>> result : matchedResult) {
			if (result.get(idIdentifier) != null) {
				for (String id : result.get(idIdentifier)) {
					if (repoId.trim().equals(id)) {
						rtn = result;
						return rtn;
					}
				}
			}
		}
		return rtn;
	}

	public Hashtable<String, ArrayList<String>> findRepoStatus(ArrayList<Hashtable<String, ArrayList<String>>> matchedResult, String repoId) {
		Hashtable<String, ArrayList<String>> rtn = new Hashtable<String, ArrayList<String>>();
		for (Hashtable<String, ArrayList<String>> result : matchedResult) {
			if (repoId.equals(result.get("Repository").get(0))) {
				rtn = result;
				return rtn;
			}
		}
		return rtn;
	}

	public String findRepoSchedule(ArrayList<Hashtable<String, String>> matchedResult, String repoId) { for (Hashtable<String, String> result : matchedResult) {
			if (repoId.equals(result.get("repoScheduleLabel"))) {
				String rtn = result.get("repoSchedule");
				return rtn;
			}
		}
		return null;
	}

	public int getRepoPkgCountFromStatus(ArrayList<Hashtable<String, ArrayList<String>>> matchedResult, String repoId, String idIdentifier) {
		for (Hashtable<String, ArrayList<String>> result : matchedResult) {
			if (repoId.equals(result.get(idIdentifier).get(0))) {
				return Integer.parseInt(result.get("Number of Packages").get(0));
			}
		}
		return -1;
	}

	public int getRepoPkgCountFromStatus(ArrayList<Hashtable<String, ArrayList<String>>> matchedResult, String repoId) {
		return getRepoPkgCountFromStatus(matchedResult, repoId, "Id");
	}

	public ArrayList<String> format(String raw) {
		// remove the [], [] and u' and return a comma seperated string
		ArrayList<String> rtn = new ArrayList<String>();
		if (raw == null) {
			return null;
		}

		raw = raw.replace("\\{", "").replace("\\}", "").replace("\\[", "").replace("\\]", "");
		String[] pieces = raw.split(",");
		for (String p : pieces) {
			rtn.add(p);
		}
		return rtn;
	}

	// Override
	public ArrayList<Hashtable<String, String>> match(String input) throws NullPointerException{
		ArrayList<Hashtable<String, String>> rtn = new ArrayList<Hashtable<String, String>>();
		// TODO: Find a better way than O(n^2)
		try {
			for (String line : input.split("\n")) {
				// use repoId as a divider
			}
		}
		catch (NullPointerException e) {
			throw e;			
		}
		return rtn;
	}
}
