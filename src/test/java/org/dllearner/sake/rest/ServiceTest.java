package org.dllearner.sake.rest;

import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Simon Bin on 30/11/16.
 */
public class ServiceTest {
	final static Logger logger = LoggerFactory.getLogger(ServiceTest.class);

	final static String json = "{\n" +
			"        \"comment\": [\"Father Example\",\n" +
			"        \"\",\n" +
			"        \"possible solution:\",\n" +
			"        \"   male AND EXISTS hasChild.TOP\",\n" +
			"        \"\",\n" +
			"        \"Copyright (C) 2007, Jens Lehmann\",\n" +
			"        \"Converted from father.conf\"],\n" +
			"        \"prefixes\": {\n" +
			"                \"comment\": \"declare some prefixes to use as abbreviations\",\n" +
			"                \"ex\": \"http://example.com/father#\"\n" +
			"        },\n" +
			"        \"ks\": {\n" +
			"                \"comment\": \"knowledge source definition\",\n" +
			"                \"type\": \"OWL File\",\n" +
			"                \"fileName\": \"examples/father.owl\"\n" +
			"        },\n" +
			"        \"reasoner\": {\n" +
			"                \"comment\": \"reasoner\",\n" +
			"                \"type\": \"closed world reasoner\",\n" +
			"                \"sources\": [\"#ks\"]\n" +
			"        },\n" +
			"        \"learningProblem\": {\n" +
			"                \"comment\": \"learning problem\",\n" +
			"                \"type\": \"posNegStandard\",\n" +
			"                \"positiveExamples\": [\"ex:stefan\",\n" +
			"                \"ex:markus\",\n" +
			"                \"ex:martin\"],\n" +
			"                \"negativeExamples\": [\"ex:heinz\",\n" +
			"                \"ex:anna\",\n" +
			"                \"ex:michelle\"]\n" +
			"        },\n" +
			"        \"algorithm\": {\n" +
			"                \"comment\": \"create learning algorithm to run\",\n" +
			"                \"type\": \"celoe\",\n" +
			"                \"maxExecutionTimeInSeconds\": 1\n" +
			"        },\n" +
			"        \"verbalisation\": true\n" +
			"}\n";

	public static void main(String[] args) throws ParseException {
		WorkQueue queue = new WorkQueue();


		JSONParser parser = new JSONParser();
		Map in;

		Object parse = parser.parse(json, new InsertionOrderedContainerFactory());
		logger.trace("parse was: " + parse.toString());
		in = (Map) parse;

		boolean verbalisation = (boolean) in.get("verbalisation");
		WorkQueueEntry entry = queue.enqueue(in, verbalisation);
		HashMap<Long, Object> longObjectHashMap = queue.awaitTermination();
		System.err.println(JSONValue.toJSONString(longObjectHashMap));
	}
}
