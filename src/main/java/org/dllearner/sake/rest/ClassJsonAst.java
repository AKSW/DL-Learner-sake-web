package org.dllearner.sake.rest;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.vocab.OWLFacet;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * OWLClassExpression to simple structure converter
 */
public class ClassJsonAst implements OWLClassExpressionVisitor, OWLPropertyExpressionVisitor, OWLDataRangeVisitor, OWLDataVisitor {
	private Object ds;
	private Object ptr;

	public static Object convert(OWLClassExpression ce) {
		ClassJsonAst conv = new ClassJsonAst();
		synchronized (conv) {
			ce.accept(conv);
			return conv.ds;
		}
	}

	private static Object convert(OWLPropertyExpression property) {
		ClassJsonAst conv = new ClassJsonAst();
		synchronized (conv) {
			property.accept(conv);
			return conv.ds;
		}
	}

	private static Object convert(OWLDataRange dr) {
		ClassJsonAst conv = new ClassJsonAst();
		synchronized (conv) {
			dr.accept(conv);
			return conv.ds;
		}
	}

	private static Object convert(OWLFacetRestriction restriction) {
		ClassJsonAst conv = new ClassJsonAst();
		synchronized (conv) {
			restriction.accept(conv);
			return conv.ds;
		}
	}

	private static Object convert(OWLLiteral literal) {
		ClassJsonAst conv = new ClassJsonAst();
		synchronized (conv) {
			literal.accept(conv);
			return conv.ds;
		}
	}

	@Override
	public void visit(@Nonnull OWLClass ce) {
		ds = Arrays.asList("Class", iri(ce));
	}

	@Override
	public void visit(@Nonnull OWLObjectIntersectionOf ce) {
		ds = operandList("ObjectIntersectionOf", ce);
	}

	private List<Object> operandList(String type, OWLNaryBooleanClassExpression ce) {
		List<Object> r = new LinkedList();
		r.add(type);
		for (OWLClassExpression e : ce.getOperandsAsList()) {
			r.add(convert(e));
		}
		return r;
	}

	private List<Object> operandList(String type, OWLNaryDataRange dr) {
		List<Object> r = new LinkedList();
		r.add(type);
		SortedSet<OWLDataRange> operands = new TreeSet<>(dr.getOperands());
		for (OWLDataRange e : operands) {
			r.add(convert(e));
		}
		return r;
	}

	private List<Object> operand(String type, OWLObjectComplementOf ce) {
		return Arrays.asList(type, convert(ce.getOperand()));
	}

	private List<Object> operand(String type, OWLObjectInverseOf ce) {
		return Arrays.asList(type, convert(ce.getInverse()));
	}

	@Override
	public void visit(@Nonnull OWLObjectUnionOf ce) {
		ds = operandList("ObjectUnionOf", ce);
	}

	@Override
	public void visit(@Nonnull OWLObjectComplementOf ce) {
		ds = operand("ObjectComplementOf", ce);
	}

	@Override
	public void visit(@Nonnull OWLObjectSomeValuesFrom ce) {
		ds = operandRole("ObjectSomeValuesFrom", ce);
	}

	private List<Object> operandRole(String type, OWLQuantifiedObjectRestriction ce) {
		return Arrays.asList(type, convert(ce.getProperty()), convert(ce.getFiller()));
	}

	private List<Object> operandRole(String type, OWLQuantifiedDataRestriction ce) {
		return Arrays.asList(type, convert(ce.getProperty()), convert(ce.getFiller()));
	}

	@Override
	public void visit(@Nonnull OWLObjectAllValuesFrom ce) {
		ds = operandRole("ObjectAllValuesFrom", ce);
	}

	@Override
	public void visit(@Nonnull OWLObjectHasValue ce) {
		ds = convert(ce.asSomeValuesFrom());
	}

	@Override
	public void visit(@Nonnull OWLObjectMinCardinality ce) {
		ds = operandCardinality("ObjectMinCardinality", ce);
	}

	private List<Object> operandCardinality(String type, OWLObjectCardinalityRestriction ce) {
		return Arrays.asList(type, ce.getCardinality(), convert(ce.getProperty()), convert(ce.getFiller()));
	}

	private List<Object> operandCardinality(String type, OWLDataCardinalityRestriction ce) {
		return Arrays.asList(type, ce.getCardinality(), convert(ce.getProperty()), convert(ce.getFiller()));
	}

	@Override
	public void visit(@Nonnull OWLObjectExactCardinality ce) {
		ds = operandCardinality("ObjectExactCardinality", ce);
	}

	@Override
	public void visit(@Nonnull OWLObjectMaxCardinality ce) {
		ds = operandCardinality("ObjectMaxCardinality", ce);
	}

	@Override
	public void visit(@Nonnull OWLObjectHasSelf ce) {
		ds = Arrays.asList("ObjectHasSelf", convert(ce.getProperty()));
	}

	@Override
	public void visit(@Nonnull OWLObjectOneOf ce) {
		ds = convert(ce.asObjectUnionOf());
	}

	@Override
	public void visit(@Nonnull OWLDataSomeValuesFrom ce) {
		ds = operandRole("DataSomeValuesFrom", ce);
	}

	@Override
	public void visit(@Nonnull OWLDataAllValuesFrom ce) {
		ds = operandRole("DataAllValuesFrom", ce);
	}

	@Override
	public void visit(@Nonnull OWLDataHasValue ce) {
		ds = convert(ce.asSomeValuesFrom());
	}

	@Override
	public void visit(@Nonnull OWLDataMinCardinality ce) {
		ds = operandCardinality("DataMinCardinality", ce);
	}

	@Override
	public void visit(@Nonnull OWLDataExactCardinality ce) {
		ds = operandCardinality("DataExactCardinality", ce);
	}

	@Override
	public void visit(@Nonnull OWLDataMaxCardinality ce) {
		ds = operandCardinality("DataMaxCardinality", ce);
	}

	private String iri(OWLEntity e) {
		return e.toStringID();
	}

	@Override
	public void visit(@Nonnull OWLObjectProperty property) {
		ds = iri(property);
	}

	@Override
	public void visit(@Nonnull OWLObjectInverseOf property) {
		ds = operand("ObjectInverseOf", property);
	}

	@Override
	public void visit(@Nonnull OWLDataProperty property) {
		ds = iri(property);
	}

	@Override
	public void visit(@Nonnull OWLAnnotationProperty property) {
		ds = iri(property);
	}

	@Override
	public void visit(@Nonnull OWLDatatype node) {
		ds = iri(node);
	}

	@Override
	public void visit(@Nonnull OWLDataOneOf node) {
		ds = operandList("DataOneOf", node);
	}

	private List<Object> operandList(String type, OWLDataOneOf node) {
		List<Object> r = new LinkedList();
		r.add(type);
		TreeSet<OWLLiteral> values = new TreeSet<>(node.getValues());
		for (OWLLiteral e : values) {
			r.add(convert(e));
		}
		return r;
	}

	@Override
	public void visit(@Nonnull OWLDataComplementOf node) {
		ds = Arrays.asList("DataComplementOf", convert(node.getDataRange()));
	}

	@Override
	public void visit(@Nonnull OWLDataIntersectionOf node) {
		ds = operandList("DataIntersectionOf", node);
	}

	@Override
	public void visit(@Nonnull OWLDataUnionOf node) {
		ds = operandList("DataUnionOf", node);
	}

	@Override
	public void visit(@Nonnull OWLDatatypeRestriction node) {
		List<Object> r = new LinkedList<>();
		r.add("DatatypeRestriction");
		r.add(convert(node.getDatatype()));
		TreeSet<OWLFacetRestriction> restrictions = new TreeSet<>(node.getFacetRestrictions());
		for (OWLFacetRestriction rst : restrictions) {
			r.add(convert(rst));
		}
		ds = r;
	}

	@Override
	public void visit(@Nonnull OWLLiteral node) {
		ds = node.getLiteral();
	}

	@Override
	public void visit(@Nonnull OWLFacetRestriction node) {
		OWLFacet facet = node.getFacet();
		OWLLiteral facetValue = node.getFacetValue();
		ds = Arrays.asList("FacetRestriction", facet.getIRI().toString(), convert(facetValue));
	}

}
