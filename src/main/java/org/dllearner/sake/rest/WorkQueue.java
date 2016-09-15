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

	public Pair<Long,Future<DlLearnerRunner>> enqueue(final CLIBase2 dlLearner, boolean verbalisation) {
		Future<DlLearnerRunner> future = executor.submit(new DlLearnerRunner(dlLearner, id, verbalisation));
		Pair<Long, Future<DlLearnerRunner>> ret = Pair.of(id, future);
		queue.add(Triple.of(id,dlLearner,future));
		synchronized (this) { id ++; }
		return ret;
	}

	private static List<Map<String, Object>> getResultList(AbstractCELA la, boolean verbalisation) {
		List<Map<String, Object>> str = new LinkedList<>();
		VerbalisationHelper verbalisationHelper = null;
		if (verbalisation) verbalisationHelper = new VerbalisationHelper();

		for (EvaluatedDescription<? extends Score> ed : la.getCurrentlyBestEvaluatedDescriptions().descendingSet()) {
			// temporary code
			OWLClassExpression description = ed.getDescription();
			String descriptionString = StringRenderer.getRenderer().render(description);
			AbstractClassExpressionLearningProblem<? extends Score> learningProblem = la.getLearningProblem();
			LinkedHashMap<String, Object> desc = new LinkedHashMap<>();
			desc.put("description", descriptionString);
			desc.put("description.ast", ClassJsonAst.convert(description));
			if (verbalisationHelper != null)
				desc.put("verbalisation", verbalisationHelper.verb(description));

			if (learningProblem instanceof PosNegLP) {
				Set<OWLIndividual> positiveExamples = ((PosNegLP) learningProblem).getPositiveExamples();
				Set<OWLIndividual> negativeExamples = ((PosNegLP) learningProblem).getNegativeExamples();
				ReasoningUtils reasoningUtil = learningProblem.getReasoningUtil();
				desc.put("predictive accuracy",
						reasoningUtil.getAccuracyOrTooWeak2(new AccMethodPredAcc(true), description, positiveExamples, negativeExamples, 1));
				desc.put("F-measure", reasoningUtil.getAccuracyOrTooWeak2(new AccMethodFMeasure(true), description, positiveExamples, negativeExamples, 1));

				AccMethodTwoValued accuracyMethod = ((PosNegLP) learningProblem).getAccuracyMethod();
				if (!(accuracyMethod instanceof AccMethodPredAcc)
						&& !(accuracyMethod instanceof AccMethodFMeasure)) {
					desc.put(AnnComponentManager.getName(accuracyMethod), ed.getAccuracy());
				}
			} else {
				desc.put("accuracy", ed.getAccuracy());
			}
			str.add(desc);
		}

		return str;
	}

	public Map<Long,Object> getList() {
		HashMap<Long, Object> res = new HashMap<>();
		for (Triple<Long, CLIBase2, Future<DlLearnerRunner>> f : queue) {
			res.put(f.getLeft(), getEntry(f.getMiddle(), f.getRight()));
		}
		return res;
	}

	private Object getEntry(CLIBase2 learner, Future<DlLearnerRunner> future) {
		if (future.isCancelled()) {
			return  "cancelled";
		} else if (future.isDone()) {
			try {
				DlLearnerRunner dlLearnerRunner = future.get();
				if (dlLearnerRunner.isDone()) {
					Map<String, Object> res2 = new LinkedHashMap<>();
					res2.put("_state","done");
					Map<String, AbstractCELA> algorithmMap = learner.getContext().getBeansOfType(AbstractCELA.class);
					Iterator<Map.Entry<String, AbstractCELA>> it = algorithmMap.entrySet().iterator();
					while (it.hasNext()) {
						Map.Entry<String, AbstractCELA> e = it.next();
						String name = e.getKey();
						AbstractCELA la = e.getValue();
						res2.put(name, getResultList(la, dlLearnerRunner.isVerbalisation()));
					}
					return res2;
				} else {
					return "exited with error";
				}
			} catch (InterruptedException e) {
				return "interrupted";
			} catch (ExecutionException e) {
				Map<String, String> res2 = new HashMap<>();
				res2.put("exception", e.getMessage());
				return res2;
			}
		} else {
			Map<String, AbstractCELA> algorithmMap = learner.getContext().getBeansOfType(AbstractCELA.class);
			Iterator<Map.Entry<String, AbstractCELA>> it = algorithmMap.entrySet().iterator();
			Map<String, Object> res2 = new LinkedHashMap<>();
			res2.put("_state", "waiting");
			while (it.hasNext()) {
				Map.Entry<String, AbstractCELA> e = it.next();
				String name = e.getKey();
				AbstractCELA la = e.getValue();
				if (la.isRunning()) {
					Map<String, Object> details = new LinkedHashMap<>();
					details.put("_state", "running");

					OWLClassExpression currentlyBestDescription = la.getCurrentlyBestDescription();
					details.put("current best description", StringRenderer.getRenderer().render(currentlyBestDescription));
					details.put("current best description.ast", ClassJsonAst.convert(currentlyBestDescription));
					res2.put(name, details);
				} else {
					res2.put(name, "not running");
				}
			}
			return res2;
		}
	}

	public Object get(long id) {
		Triple<Long, CLIBase2, Future<DlLearnerRunner>> f = getQueueEntry(id);
		if (f != null) {
			return getEntry(f.getMiddle(), f.getRight());
		}
		return null;
	}

	private Triple<Long, CLIBase2, Future<DlLearnerRunner>> getQueueEntry(long id) {
		for (Triple<Long, CLIBase2, Future<DlLearnerRunner>> f : queue) {
			if (f.getLeft().longValue() == id) {
				return f;
			}
		}
		return null;
	}

	public boolean delete(long id) {
		Triple<Long, CLIBase2, Future<DlLearnerRunner>> f = getQueueEntry(id);
		if (f != null) {
			boolean ret = true;
			if (!f.getRight().isDone()) {
				ret = f.getRight().cancel(true);
			}
			boolean removed = queue.remove(f);
			return ret && removed;
		}
		return false;
	}
}
