package org.dllearner.sake.rest;

import org.dllearner.core.ComponentInitException;
import org.dllearner.kb.SparqlEndpointKS;
import org.dllearner.reasoning.SPARQLReasoner;
import org.dllearner.utilities.OWLAPIUtils;
import org.json.simple.JSONObject;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Simon Bin on 22/09/16.
 */
public class ClassJsonAstTest {
	public static void main(String[] args) throws MalformedURLException, ComponentInitException {
		OWLDataFactory df = OWLManager.getOWLDataFactory();
		SparqlEndpointKS ks = new SparqlEndpointKS();
		ks.setUseCache(false);

		ks.setUrl(new URL("http://sake.informatik.uni-leipzig.de:8850/sparql"));
		ks.setDefaultGraphURIs(Collections.singletonList("http://sake-projekt.de"));
		ks.init();
		SPARQLReasoner sparqlReasoner = new SPARQLReasoner(ks);
		sparqlReasoner.setLaxMode(true);
		sparqlReasoner.init();

		OWLClassExpression owlClassExpression = OWLAPIUtils.fromManchester("Event and (containsFailureData some (FailureData and (hasContext some (Context and (Context_11 or Context_15)))))", sparqlReasoner, df, true);
		Map data = new HashMap<String,Object>();
		data.put("ast", ClassJsonAst.convert(owlClassExpression));
		JSONObject jsonObject = new JSONObject(data);
		System.err.println(jsonObject.toJSONString());
		VerbalisationHelper verbalisationHelper = new VerbalisationHelper();
		String verb = verbalisationHelper.verb(owlClassExpression);
		System.err.println(verb);
	}
}
