package org.dllearner.sake.rest;

import org.apache.commons.collections.list.SynchronizedList;
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
 * Queue of jobs to work on
 */
public class WorkQueue {
	private List<WorkQueueEntry> queue
			= SynchronizedList.decorate(new LinkedList<WorkQueueEntry>());
	private final int threads = 1;
	private ExecutorService executor = Executors.newFixedThreadPool(threads);
	private long id = 0;

	public WorkQueueEntry enqueue(Map jsonConfig, boolean verbalisation) {
		Future<DlLearnerConfigurator> future = executor.submit(new DlLearnerConfigurator(executor, id, jsonConfig, verbalisation));
		final WorkQueueEntry ret = new WorkQueueEntry(id, jsonConfig, verbalisation, future);
		queue.add(ret);
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
		for (WorkQueueEntry f : queue) {
			res.put(f.getId(), getConfiguratorEntry(f.getFuture()));
		}
		return res;
	}

	private Object getRunnerEntry(CLIBase2 learner, Future<DlLearnerRunner> runnerFuture) {

		if (runnerFuture.isCancelled()) {
			return "cancelled";
		} else if (runnerFuture.isDone()) {
			DlLearnerRunner dlLearnerRunner;
			try {
				dlLearnerRunner = runnerFuture.get();
			} catch (InterruptedException e) {
				return "interrupted";
			} catch (ExecutionException e) {
				Map<String, String> res2 = new HashMap<>();
				res2.put("exception", e.getMessage());
				return res2;
			}

			Map<String, Object> res2 = new LinkedHashMap<>();
			res2.put("_state", "done");
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

	private Object getConfiguratorEntry(Future<DlLearnerConfigurator> configuratorFuture) {
		if (configuratorFuture.isCancelled()) {
			return  "cancelled";
		} else if (configuratorFuture.isDone()) {
			DlLearnerConfigurator dlLearnerConfigurator = null;
			try {
				dlLearnerConfigurator = configuratorFuture.get();
			} catch (InterruptedException e) {
				return "interrupted";
			} catch (ExecutionException e) {
				Map<String, String> res2 = new HashMap<>();
				res2.put("exception", e.getMessage());
				return res2;
			}

			if (!dlLearnerConfigurator.isDone())
				return "exited with error";

			return getRunnerEntry(dlLearnerConfigurator.getDlLearner(), dlLearnerConfigurator.getRunnerFuture());

		} else {
			return  "configuring";
		}
	}

	public Object get(long id) {
		WorkQueueEntry f = getQueueEntry(id);
		if (f != null) {
			return getConfiguratorEntry(f.getFuture());
		}
		return null;
	}

	private WorkQueueEntry getQueueEntry(long id) {
		for (WorkQueueEntry f : queue) {
			if (f.getId().longValue() == id) {
				return f;
			}
		}
		return null;
	}

	public boolean delete(long id) {
		WorkQueueEntry f = getQueueEntry(id);
		if (f != null) {
			boolean ret = true;
			if (!f.getFuture().isDone()) {
				ret = f.getFuture().cancel(true);
			} else {
				try {
					DlLearnerConfigurator dlLearnerConfigurator = f.getFuture().get();
					if (!dlLearnerConfigurator.getRunnerFuture().isDone()) {
						ret = dlLearnerConfigurator.getRunnerFuture().cancel(true);
					}
				} catch (InterruptedException | ExecutionException e) {
					// ignore
				}
			}
			boolean removed = queue.remove(f);
			return ret && removed;
		}
		return false;
	}

	public void shutdown() {
		executor.shutdown();
	}

	public synchronized HashMap<Long, Object> awaitTermination() {
		HashMap<Long, Object> res = new HashMap<>();

		while (!queue.isEmpty()) {
			WorkQueueEntry entry = queue.remove(0);
			try {
				DlLearnerConfigurator dlLearnerConfigurator = entry.getFuture().get();
				if (dlLearnerConfigurator.isDone())
					dlLearnerConfigurator.getRunnerFuture().get();
			} catch (InterruptedException  | ExecutionException e) {
				e.printStackTrace();
			}
			res.put(entry.getId(), getConfiguratorEntry(entry.getFuture()));
		}
		shutdown();
		return res;
	}
}
