package org.dllearner.sake.rest;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.dllearner.cli.CLI;
import org.dllearner.cli.CLIBase2;
import org.dllearner.configuration.IConfiguration;
import org.dllearner.configuration.spring.DefaultApplicationContextBuilder;
import org.dllearner.confparser.ConfParserConfiguration;
import org.dllearner.core.ComponentInitException;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;


import javax.servlet.ServletContext;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;

/**
 * Created by Simon Bin on 16-8-19.
 */
@Path("")
public class Service {
  private static final Logger logger = Logger.getLogger(Service.class);
  private static WorkQueue queue = new WorkQueue();
  private static Map<String, Long> jobMap = new HashMap<String, Long>();

  @GET
  public Response index(@HeaderParam("Accept") String header, @Context ServletContext context) {
    if (header.contains(MediaType.APPLICATION_JSON)) {
      LinkedHashMap<String, String> res = new LinkedHashMap<>();
      res.put("/list", "GET details of all jobs");
      res.put("/submit", "POST new job with JSON body");
      res.put("/submitform", "POST new job with 'json' and 'jobid' as parameters");
      res.put("/{id}", "GET details of job {id}");
      res.put("/job/{jobid}", "GET details of job {jobid}");
      res.put("/{id}", "DELETE job {id}");
      res.put("/job/{jobid}", "DELETE job {jobid}");
      return Response.ok((new JSONObject(res)).toJSONString(), MediaType.APPLICATION_JSON_TYPE)
          .build();
    }
    return Response.ok("DL-Learner Rest Service" + "\n" + "/list" + "\t" + "GET" + "\t"
        + "get details of all jobs" + "\n" + "/submit" + "\t" + "POST" + "\t"
        + "submit a new job with JSON in body" + "\n" + "/submitform" + "\t" + "POST" + "\t"
        + "new job with 'json' and 'jobid' as parameters" + "\n" + "/{id}" + "\t" + "GET" + "\t"
        + "get details of job {id}" + "\n" + "/job/{jobid}" + "\t" + "GET" + "\t"
        + "get details of job {jobid}" + "\n" + "/{id}" + "\t" + "DELETE" + "\t" + "delete job {id}"
        + "\n" + "/job/{jobid}" + "\t" + "DELETE" + "\t" + "delete job {jobid}" + "\n",
        MediaType.TEXT_PLAIN_TYPE).build();
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
        + "<label>Job ID</label><input type=\"text\" name=\"jobid\"><br/>"
        + "<input type=\"submit\" /></form></body></html>";
  }

  @POST
  @Path("/submitform")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  public Response submitForm(@FormParam("json") String json, @FormParam("jobid") String jobid,
      @Context ServletContext servletContext) throws IOException {
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

    // in.writeJSONString();
    DlConfigConverter dlConfig = new DlConfigConverter();
    String configStr = dlConfig.convert(in);
    logger.trace("converted: " + configStr);

    ByteArrayInputStream stream = new ByteArrayInputStream(configStr.getBytes());
    IConfiguration configuration = new ConfParserConfiguration(new InputStreamResource(stream));
    DefaultApplicationContextBuilder builder = new DefaultApplicationContextBuilder();
    ApplicationContext context;
    try {
      context = builder.buildApplicationContext(configuration, new ArrayList<Resource>());
    } catch (Exception e) {
      Throwable primaryCause = findPrimaryCause(e);
      logger.error("Error during the creation of the context",e);
      if (primaryCause != null) {
        ret.put("error", primaryCause.getMessage());
      } else {
        ret.put("error", e.getMessage());
        ret.put("stackTrace", e.getStackTrace());
        // throw e;
      }
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ret.toJSONString())
          .build();
    }
    CLIBase2 dlLearner;
    if (context.containsBean("cli")) {
      dlLearner = (CLIBase2) context.getBean("cli");
    } else {
      dlLearner = new CLI();
    }
    dlLearner.setContext(context);
    // dlLearner.setConfFile();

    Pair<Long, Future<DlLearnerRunner>> q = queue.enqueue(dlLearner, verbalisation);
    ret.put("queueId", q.getLeft());
    ret.put("jobId", jobId);
    // add to job map
    jobMap.put(jobId, q.getLeft());
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
}
