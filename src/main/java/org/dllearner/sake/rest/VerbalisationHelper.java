package org.dllearner.sake.rest;

import org.aksw.owl2nl.OWLAxiomConverter;
import org.aksw.owl2nl.OWLClassExpressionConverter;
import org.aksw.owl2nl.exception.OWLAxiomConversionException;
import org.dllearner.core.ComponentInitException;
import org.dllearner.kb.OWLFile;
import org.dllearner.reasoning.OWLAPIReasoner;
import org.dllearner.utilities.OWLAPIUtils;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.io.File;

/**
 * Verbalise result
 */
public class VerbalisationHelper {
	public static final String NS = "http://www.benchmark.org/family#";
	//private final OWLAxiomConverter owlAxiomConverter;
	private OWLOntology ontology;
	private OWLOntologyManager manager;
	private OWLDataFactory df;
	private final OWLClassExpressionConverter converter;


	public VerbalisationHelper() {
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		df = manager.getOWLDataFactory();
		converter = new OWLClassExpressionConverter();
		//owlAxiomConverter = new OWLAxiomConverter(ontology);
	}

	public String verb(OWLClassExpression ce) {
		String s = null;
		s = converter.convert(ce);
		return s;
	}

	public static void main(String[] args) throws ComponentInitException, OWLOntologyCreationException, OWLAxiomConversionException {
		VerbalisationHelper verbalisationHelper = new VerbalisationHelper();
		OWLDataFactory df = OWLManager.getOWLDataFactory();
		String ont = "/home/ailin/DL-Learner/examples/family-benchmark/family-benchmark.owl";
		OWLFile owlFile = new OWLFile(ont);
		OWLAPIReasoner rc = new OWLAPIReasoner(owlFile);
		rc.init();
		OWLClassExpression owlClassExpression0 = OWLAPIUtils.fromManchester("Male and (hasSibling some (hasChild some Person))", rc, df, true);
		OWLClassExpression owlClassExpression1 = OWLAPIUtils.fromManchester("Male and ((hasSibling some (hasChild some Person)) or (married some (hasSibling some (hasChild some Thing)))) and (not (Female))", rc, df, true);
		//OWLClassExpression owlClassExpression0 = OWLAPIUtils.fromManchester("married some Thing", rc, df, true);
		OWLOntologyManager owlOntologyManager = OWLManager.createOWLOntologyManager();
		OWLOntology owlOntology0 = owlOntologyManager.loadOntologyFromOntologyDocument(new File(ont));
		OWLOntology owlOntology1 = owlOntologyManager.createOntology();
		OWLAxiomConverter owlAxiomConverter0 = new OWLAxiomConverter(owlOntology0);
		OWLAxiomConverter owlAxiomConverter1 = new OWLAxiomConverter(owlOntology1);
		OWLEquivalentClassesAxiom axiom0 = df.getOWLEquivalentClassesAxiom(df.getOWLClass(IRI.create(NS + "Uncle")), owlClassExpression0);
		OWLEquivalentClassesAxiom axiom1 = df.getOWLEquivalentClassesAxiom(df.getOWLClass(IRI.create(NS + "Uncle")), owlClassExpression1);
		System.out.println("" + owlAxiomConverter0.convert(axiom0));
		System.out.println("" + owlAxiomConverter1.convert(axiom0));
		System.out.println("" + owlAxiomConverter0.convert(axiom1));
		System.out.println("" + owlAxiomConverter1.convert(axiom1));
	}
}
