package org.dllearner.sake.rest;

import org.apache.commons.collections.list.SynchronizedList;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.dllearner.cli.CLIBase2;
import org.dllearner.core.*;
import org.dllearner.learningproblems.AccMethodFMeasure;
import org.dllearner.learningproblems.AccMethodPredAcc;
import org.dllearner.learningproblems.AccMethodTwoValued;
import org.dllearner.learningproblems.PosNegLP;
import org.dllearner.utilities.ReasoningUtils;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLIndividual;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by Simon Bin on 16-8-22.
 */
public class WorkQueue {
	private List<Triple<Long,CLIBase2,Future<DlLearnerRunner>>> queue
			= SynchronizedList.decorate(new LinkedList<Triple<Long,CLIBase2,Future<DlLearnerRunner>>>());
	private final int threads = 1;
	private ExecutorService executor = Executors.newFixedThreadPool(threads);
	private long id = 0;

	public Pair<Long,Future<DlLearnerRunner>> enqueue(final CLIBase2 dlLearner) {
		Future<DlLearnerRunner> future = executor.submit(new DlLearnerRunner(dlLearner, id));
		queue.add(Triple.of(id,dlLearner,future));
		synchronized (this) { id ++; }
		return Pair.of(id,future);
	}

	private static String getResultString(AbstractCELA la) {
		int current = 1;
		String str = "";
		DecimalFormat dfPercent = new DecimalFormat("0.00%");

		for (EvaluatedDescription<? extends Score> ed : la.getCurrentlyBestEvaluatedDescriptions().descendingSet()) {
			// temporary code
			OWLClassExpression description = ed.getDescription();
			String descriptionString = StringRenderer.getRenderer().render(description);
			AbstractClassExpressionLearningProblem<? extends Score> learningProblem = la.getLearningProblem();
			if (learningProblem instanceof PosNegLP) {
				Set<OWLIndividual> positiveExamples = ((PosNegLP) learningProblem).getPositiveExamples();
				Set<OWLIndividual> negativeExamples = ((PosNegLP) learningProblem).getNegativeExamples();
				ReasoningUtils reasoningUtil = learningProblem.getReasoningUtil();

				str += current + ": " + descriptionString + " (pred. acc.: "
						+ dfPercent.format(reasoningUtil.getAccuracyOrTooWeak2(new AccMethodPredAcc(true), description, positiveExamples, negativeExamples, 1))
						+ ", F-measure: " + dfPercent.format(reasoningUtil.getAccuracyOrTooWeak2(new AccMethodFMeasure(true), description, positiveExamples, negativeExamples, 1));

				AccMethodTwoValued accuracyMethod = ((PosNegLP) learningProblem).getAccuracyMethod();
				if (!(accuracyMethod instanceof AccMethodPredAcc)
						&& !(accuracyMethod instanceof AccMethodFMeasure)) {
					str += ", " + AnnComponentManager.getName(accuracyMethod) + ": " + dfPercent.format(ed.getAccuracy());
				}
				str += ")\n";
			} else {
				str += current + ": " + descriptionString + " " + dfPercent.format(ed.getAccuracy()) + "\n";
			}
			current++;
		}

		return str;
	}

	public Map<Long,Object> getList() {
		HashMap<Long, Object> res = new HashMap<>();
		for (Triple<Long, CLIBase2, Future<DlLearnerRunner>> f : queue) {
			if (f.getRight().isCancelled()) {
				res.put(f.getLeft(), "cancelled");
			} else if (f.getRight().isDone()) {
				try {
					DlLearnerRunner dlLearnerRunner = f.getRight().get();
					if (dlLearnerRunner.isDone()) {
						HashMap<String,String > res2 = new HashMap<String, String>();
						res2.put("_state","done");
						CLIBase2 learner = dlLearnerRunner.getLearner();
						Map<String, AbstractCELA> algorithmMap = learner.getContext().getBeansOfType(AbstractCELA.class);
						Iterator<Map.Entry<String, AbstractCELA>> it = algorithmMap.entrySet().iterator();
						while (it.hasNext()) {
							Map.Entry<String, AbstractCELA> e = it.next();
							String name = e.getKey();
							AbstractCELA la = e.getValue();
							res2.put(name, getResultString(la));
						}
						res.put(f.getLeft(), res2);
					} else {
						res.put(f.getLeft(), "exited with error");
					}
				} catch (InterruptedException e) {
					res.put(f.getLeft(), "interrupted");
				} catch (ExecutionException e) {
					res.put(f.getLeft(), "exception: " + e.getMessage());
				}
			} else {
				CLIBase2 learner = f.getMiddle();
				Map<String, AbstractCELA> algorithmMap = learner.getContext().getBeansOfType(AbstractCELA.class);
				Iterator<Map.Entry<String, AbstractCELA>> it = algorithmMap.entrySet().iterator();
				HashMap<String,String > res2 = new HashMap<String, String>();
				res2.put("_state", "waiting");
				while (it.hasNext()) {
					Map.Entry<String, AbstractCELA> e = it.next();
					String name = e.getKey();
					AbstractCELA la = e.getValue();
					if (la.isRunning()) {
						res2.put(name, "current best: " + StringRenderer.getRenderer().render(la.getCurrentlyBestDescription()));
					} else {
						res2.put(name, "not running");
					}
				}
				res.put(f.getLeft(), res2);
			}
		}
		return res;
	}

	public boolean delete(long id) {
		for (Triple<Long, CLIBase2, Future<DlLearnerRunner>> f : queue) {
			if (f.getLeft().longValue() == id) {
				boolean ret = true;
				if (!f.getRight().isDone()) {
					ret = f.getRight().cancel(true);
				}
				boolean removed = queue.remove(f);
				return ret && removed;
			}
		}
		return false;
	}
}
