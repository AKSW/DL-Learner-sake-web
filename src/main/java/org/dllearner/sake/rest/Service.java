package org.dllearner.sake.rest;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.dllearner.cli.CLI;
import org.dllearner.cli.CLIBase2;
import org.dllearner.configuration.IConfiguration;
import org.dllearner.configuration.spring.DefaultApplicationContextBuilder;
import org.dllearner.confparser.ConfParserConfiguration;
import org.dllearner.core.ComponentInitException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

import javax.servlet.ServletContext;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * Created by Simon Bin on 16-8-19.
 */
@Path("")
public class Service {
	private static final Logger logger = LoggerFactory.getLogger(Service.class);
	private static WorkQueue queue = new WorkQueue();

	@GET
	public String hello(@Context ServletContext context) {
		return "DL-Learner Rest Service";
	}

	@GET
	@Path("list")
	@Produces(MediaType.APPLICATION_JSON)
	public String jobList(@Context ServletContext servletContext) {
		JSONObject jsonObject = new JSONObject(queue.getList());
		return jsonObject.toJSONString();
	}

	@POST
	@Path("submit")
    @Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public String submit(String input, @Context ServletContext servletContext) throws IOException {
		JSONObject ret = new JSONObject();
		JSONParser parser = new JSONParser();
		System.err.println("input was: " + input);
		Map in;
		try {
			Object parse = parser.parse(input, new InsertionOrderedContainerFactory());
			System.err.println("parse was: " + parse.toString());
			if (!(parse instanceof Map)) {
				ret.put("error", "Not a JSON object: " + parse.getClass());
				return ret.toJSONString();
			}
			in = (Map) parse;
		} catch (ParseException e) {
			ret.put("error", e.toString());
			return ret.toJSONString();
		}

		//in.writeJSONString();
		DlConfigConverter dlConfig = new DlConfigConverter();
		String configStr = dlConfig.convert(in);
		System.err.println("converted: " + configStr );

		ByteArrayInputStream stream = new ByteArrayInputStream(configStr.getBytes());
		IConfiguration configuration = new ConfParserConfiguration(new InputStreamResource(stream));
		DefaultApplicationContextBuilder builder = new DefaultApplicationContextBuilder();
		ApplicationContext context;
		try {
			context = builder.buildApplicationContext(configuration, new ArrayList<Resource>());
		} catch (Exception e) {
			Throwable primaryCause = findPrimaryCause(e);
			if (primaryCause != null) {
				ret.put("error", primaryCause.getMessage());
				return ret.toJSONString();
			} else {
				throw e;
			}
		}
		CLIBase2 dlLearner;
		if (context.containsBean("cli")) {
			dlLearner = (CLIBase2) context.getBean("cli");
		} else {
			dlLearner = new CLI();
		}
		dlLearner.setContext(context);
		//dlLearner.setConfFile();

		Pair<Long, Future<DlLearnerRunner>> q = queue.enqueue(dlLearner);
		ret.put("queue",q.getLeft());

		return ret.toJSONString();
	}

	@DELETE
	@Path("{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public String deleteConfiguration(@PathParam("id") String id,
	                                  @Context ServletContext context) {
		boolean ret = queue.delete(Long.parseLong(id));
		return Boolean.toString(ret);
	}

	protected static Throwable findPrimaryCause(Exception e) {
	    // The throwables from the stack of the exception
	    Throwable[] throwables = ExceptionUtils.getThrowables(e);

	    //Look For a Component Init Exception and use that as the primary cause of failure, if we find it
	    int componentInitExceptionIndex = ExceptionUtils.indexOfThrowable(e, ComponentInitException.class);

	    Throwable primaryCause;
	    if(componentInitExceptionIndex > -1) {
	        primaryCause = throwables[componentInitExceptionIndex];
	    }else {
	        //No Component Init Exception on the Stack Trace, so we'll use the root as the primary cause.
	        primaryCause = ExceptionUtils.getRootCause(e);
	    }
	    return primaryCause;
	}
}
