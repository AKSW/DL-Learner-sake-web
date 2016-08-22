package org.dllearner.sake.rest;

import org.dllearner.cli.CLIBase2;

import java.util.concurrent.Callable;

/**
 * Created by Simon Bin on 16-8-22.
 */
public class DlLearnerRunner implements Callable<DlLearnerRunner> {
	private final long id;
	CLIBase2 dlLearner;
	boolean done = false;

	public DlLearnerRunner(CLIBase2 dlLearner, long id) {
		this.dlLearner = dlLearner;
		this.id = id;
	}

	@Override
	public DlLearnerRunner call() throws Exception {
		dlLearner.run();
		this.done = true;
		return this;
	}

	public long getId() { return id; }
	public boolean isDone() { return done; }

	public CLIBase2 getLearner() {
		return dlLearner;
	}
}
