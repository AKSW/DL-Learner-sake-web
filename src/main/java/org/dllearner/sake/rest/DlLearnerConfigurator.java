package org.dllearner.sake.rest;

import org.apache.commons.io.Charsets;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.dllearner.cli.CLI;
import org.dllearner.cli.CLIBase2;
import org.dllearner.configuration.IConfiguration;
import org.dllearner.configuration.spring.DefaultApplicationContextBuilder;
import org.dllearner.confparser.ConfParserConfiguration;
import org.dllearner.core.ComponentInitException;
import org.json.simple.JSONValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Created by Simon Bin on 30/11/16.
 */
public class DlLearnerConfigurator implements Callable<DlLearnerConfigurator> {
	private static final Logger logger = LoggerFactory.getLogger(DlLearnerConfigurator.class);

	private final long id;
	private final Map jsonConfig;
	private final boolean verbalisation;
	private final ExecutorService executor;
	boolean done = false;
	DlLearnerRunner runner = null;
	private Future<DlLearnerRunner> runnerFuture = null;
	private String error;
	private StackTraceElement[] errorStack;
	private CLIBase2 dlLearner;

	public DlLearnerConfigurator(ExecutorService executor, long id, Map jsonConfig, boolean verbalisation) {
		this.executor = executor;
		this.id = id;
		this.jsonConfig = jsonConfig;
		this.verbalisation = verbalisation;
	}

	@Override
	public DlLearnerConfigurator call() throws Exception {
		Resource source = new InputStreamResource(new ByteArrayInputStream(JSONValue.toJSONString(jsonConfig).getBytes(Charsets.UTF_8)));
		IConfiguration configuration = new ConfParserConfiguration(source);

		DefaultApplicationContextBuilder builder = new DefaultApplicationContextBuilder();
		ApplicationContext context;
		try {
			context = builder.buildApplicationContext(configuration, new ArrayList<Resource>());
		} catch (Exception e) {
			Throwable primaryCause = findPrimaryCause(e);
			logger.error("Error during the creation of the context",e);
			if (primaryCause != null) {
				// error = primaryCause.getMessage();
				throw (Exception)primaryCause;
			} else {
				// error = e.getMessage();
				// errorStack = e.getStackTrace();
				throw e;
			}
		}

		if (context.containsBean("cli")) {
			dlLearner = (CLIBase2) context.getBean("cli");
		} else {
			dlLearner = new CLI();
		}
		dlLearner.setContext(context);

		runnerFuture =  executor.submit(new DlLearnerRunner(id, dlLearner, verbalisation));

		this.done = true;
		return this;
	}

	/**
	 * Helper method to get the primary error cause.
	 * @param e Exception to check
	 * @return
	 */
	protected static Throwable findPrimaryCause(Exception e) {
		// The throwables from the stack of the exception
		Throwable[] throwables = ExceptionUtils.getThrowables(e);

		// Look For a Component Init Exception and use that as the primary cause of failure, if we find
		// it
		int componentInitExceptionIndex =
				ExceptionUtils.indexOfThrowable(e, ComponentInitException.class);

		Throwable primaryCause;
		if (componentInitExceptionIndex > -1) {
			primaryCause = throwables[componentInitExceptionIndex];
		} else {
			// No Component Init Exception on the Stack Trace, so we'll use the root as the primary cause.
			primaryCause = ExceptionUtils.getRootCause(e);
		}
		return primaryCause;
	}

	public boolean isDone() {
		return done;
	}

	public Future<DlLearnerRunner> getRunnerFuture() {
		return runnerFuture;
	}

	public CLIBase2 getDlLearner() {
		return dlLearner;
	}
}
