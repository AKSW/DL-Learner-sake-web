package org.dllearner.sake.rest;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Created by Simon Bin on 16-8-19.
 */
@Path("")
public class Service {
	static {
		if (System.getProperty("log4j.configuration") == null)
			System.setProperty("log4j.configuration", "log4j.properties");
	}

	private static final Logger logger = LoggerFactory.getLogger(Service.class);
	private static WorkQueue queue = new WorkQueue();
	private static Map<String, Long> jobMap = new HashMap<>();

	@GET
	public Response index(
			@HeaderParam("Accept") String header, @Context ServletContext context) {
		if (header.contains(MediaType.APPLICATION_JSON)) {
			LinkedHashMap<String, String> res = new LinkedHashMap<>();
			res.put("/list", "GET details of all jobs");
			res.put("/submit", "POST new job with JSON body");
			res.put("/submit", "POST new job with 'json' and 'jobid' as parameters");
			res.put("/{id}", "GET details of job {id}");
			res.put("/job/{jobid}", "GET details of job {jobid}");
			res.put("/{id}", "DELETE job {id}");
			res.put("/job/{jobid}", "DELETE job {jobid}");
			return Response.ok((new JSONObject(res)).toJSONString(), MediaType.APPLICATION_JSON_TYPE)
					.build();
		} else {
			return Response.ok("DL-Learner Rest Service" + "\n"
							+ "/list" + "\t" + "GET" + "\t" + "get details of all jobs" + "\n"
							+ "/submit" + "\t" + "POST" + "\t" + "submit a new job with JSON in body" + "\n"
							+ "/submit" + "\t" + "POST" + "\t" + "new job with 'json' and 'jobid' as parameters" + "\n"
							+ "/{id}" + "\t" + "GET" + "\t" + "get details of job {id}" + "\n"
							+ "/job/{jobid}" + "\t" + "GET" + "\t" + "get details of job {jobid}" + "\n"
							+ "/{id}" + "\t" + "DELETE" + "\t" + "delete job {id}" + "\n"
							+ "/job/{jobid}" + "\t" + "DELETE" + "\t" + "delete job {jobid}" + "\n",
					MediaType.TEXT_PLAIN_TYPE).build();
		}
	}

	@GET
	@Path("/list")
	@Produces(MediaType.APPLICATION_JSON)
	public String jobList(@Context ServletContext servletContext) {
		JSONObject jsonObject = new JSONObject(queue.getList());
		return jsonObject.toJSONString();
	}

	@GET
	@Path("/submit")
	@Produces(MediaType.APPLICATION_XHTML_XML)
	public String submitFormInput(@Context ServletContext servletContext) {
		return "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \n"
				+ "      \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n"
				+ "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\">"
				+ "<head><title>Submit DL-Learner Job</title></head><body><form method=\"post\"><p>Paste your config below</p>"
				+ "<textarea name=\"json\"></textarea><br />"
				+ "<label>Job ID</label><input type=\"text\" name=\"jobid\" /><br/>"
				+ "<input type=\"submit\" /></form></body></html>";
	}

	@POST
	@Path("/submit")
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces(MediaType.APPLICATION_JSON)
	public Response submitForm(
			@FormParam("json") String json,
			@FormParam("jobid") String jobid, @Context ServletContext servletContext) throws IOException {
		return addtoQueue(json, jobid, servletContext);
	}

	@POST
	@Path("/submit")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response submit(String input, @Context ServletContext servletContext) throws IOException {
		return addtoQueue(input, null, servletContext);
	}

	/**
	 * Internal method used by submit methods
	 *
	 * @param json the JSON string with the jb configuration
	 * @param jobId an external job ID, is optional
	 * @param servletContext the context
	 * @return the response
	 */
	private Response addtoQueue(String json, String jobId, ServletContext servletContext) {
		// if no job ID is given, create one
		if (jobId == null) {
			UUID uuid = UUID.randomUUID();
			jobId = uuid.toString();
		} else {
			// check if a job with the given name already exists. So the old need to be removed
			Long qId = jobMap.get(jobId);
			if (qId != null) {
				queue.delete(qId);
				logger.info("Job already exists, remove old one before resubmitting.");
			}
		}
		JSONObject ret = new JSONObject();
		JSONParser parser = new JSONParser();
		logger.trace("input was: " + json);
		Map in;
		try {
			Object parse = parser.parse(json, new InsertionOrderedContainerFactory());
			logger.trace("parse was: " + parse.toString());
			if (!(parse instanceof Map)) {
				ret.put("error", "Not a JSON object: " + parse.getClass());
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ret.toJSONString())
						.build();
			}
			in = (Map) parse;
		} catch (ParseException e) {
			logger.error("Error during parsing JSON",e);
			ret.put("error", e.toString());
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ret.toJSONString())
					.build();
		}

		boolean verbalisation = true;
		if (in.containsKey("verbalisation")) {
			try {
				verbalisation = (boolean) in.remove("verbalisation");
			} catch (Exception e) {
				ret.put("error", e.toString());
				logger.error("Could not get verbalisation",e);
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ret.toJSONString())
						.build();
			}
			logger.debug("verbalisation is " + verbalisation);
		}

		WorkQueueEntry q = queue.enqueue(in, verbalisation);
		ret.put("queueId", q.getId());
		ret.put("jobId", jobId);
		// add to job map
		jobMap.put(jobId, q.getId());
		// return
		return Response.status(Response.Status.CREATED).entity(JSONValue.toJSONString(ret)).build();
	}

	@DELETE
	@Path("/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response deleteConfiguration(@PathParam("id") String id, @Context ServletContext context) {
		if (id == null) {
			logger.debug("id is missing.");
			return Response.status(Response.Status.BAD_REQUEST)
					.entity("{ \"code\": 400, \"error\": \"id missing.\"}").build();
		}
		boolean ret = queue.delete(Long.parseLong(id));
		return Response.status(Response.Status.OK).entity(Boolean.toString(ret)).build();
	}

	@DELETE
	@Path("/job/{jobid}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response deleteConfigurationByJobId(@PathParam("jobid") String jobid,
	                                           @Context ServletContext context) {
		if (jobid == null) {
			logger.debug("Job ID missing.");
			return Response.status(Response.Status.BAD_REQUEST)
					.entity("{ \"code\": 400, \"error\": \"jobid missing.\"}").build();
		}
		// get queue id
		Long qId = jobMap.get(jobid);
		if (qId == null) {
			logger.debug("ID "+qId+" doesnt exist.");
			return Response.status(Response.Status.NOT_FOUND)
					.entity("{ \"code\": 404, \"error\": \"job not found.\"}").build();
		}
		boolean ret = queue.delete(qId);
		return Response.status(Response.Status.OK).entity(Boolean.toString(ret)).build();
	}

	@GET
	@Path("/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response jobDetailsByQueueId(@PathParam("id") String id, @Context ServletContext context) {
		if (id == null) {
			Response.status(Response.Status.BAD_REQUEST)
					.entity("{ \"code\": 400, \"error\": \"id missing.\"}").build();
		}
		Object ret = queue.get(Long.parseLong(id));
		if (ret != null) {
			return Response.ok(JSONValue.toJSONString(ret), MediaType.APPLICATION_JSON_TYPE).build();
		}
		return Response.status(Response.Status.NOT_FOUND).build();
	}

	@GET
	@Path("/job/{jobid}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response jobDetailsByJobId(@PathParam("jobid") String jobid,
	                                  @Context ServletContext context) {
		if (jobid == null) {
			return Response.status(Response.Status.BAD_REQUEST)
					.entity("{ \"code\": 400, \"error\": \"jobid missing.\"}").build();
		}
		// get queue id
		Long qId = jobMap.get(jobid);
		if (qId == null) {
			return Response.status(Response.Status.NOT_FOUND)
					.entity("{ \"code\": 404, \"error\": \"job not found.\"}").build();
		}
		Object ret = queue.get(qId);
		if (ret != null) {
			return Response.ok(JSONValue.toJSONString(ret), MediaType.APPLICATION_JSON_TYPE).build();
		}
		return Response.status(Response.Status.NOT_FOUND).build();
	}


}
