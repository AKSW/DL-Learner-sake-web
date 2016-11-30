package org.dllearner.sake.rest;

import java.util.Map;
import java.util.concurrent.Future;

/**
 * Created by Simon Bin on 30/11/16.
 */
public class WorkQueueEntry {
	private final Map jsonConfig;
	private final boolean verbalisation;
	private final Future<DlLearnerConfigurator> future;
	private Long id;

	public WorkQueueEntry(long id, Map jsonConfig, boolean verbalisation, Future<DlLearnerConfigurator> future) {
		this.id = id;
		this.jsonConfig = jsonConfig;
		this.verbalisation = verbalisation;
		this.future = future;
	}

	public Long getId() {
		return id;
	}

	public Future<DlLearnerConfigurator> getFuture() {
		return future;
	}
}
