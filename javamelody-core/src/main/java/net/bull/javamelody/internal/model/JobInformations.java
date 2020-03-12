/*
 * Copyright 2008-2019 by Emeric Vernat
 *
 *     This file is part of Java Melody.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.bull.javamelody.internal.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.SchedulerRepository;

import net.bull.javamelody.internal.common.LOG;
import net.bull.javamelody.internal.common.Parameters;

/**
 * Informations sur un job.
 * L'état d'une instance est initialisé à son instanciation et non mutable;
 * il est donc de fait thread-safe.
 * Cet état est celui d'un job à un instant t.
 * Les instances sont sérialisables pour pouvoir être transmises au serveur de collecte.
 * Pour l'instant seul quartz est géré.
 * @author Emeric Vernat
 */
public class JobInformations implements Serializable {
	public static final boolean QUARTZ_AVAILABLE = isQuartzAvailable();
	private static final long serialVersionUID = -2826168112578815952L;
	private final String group;
	private final String name;
	private final String description;
	private final String jobClassName;
	private final Date previousFireTime;
	private final Date nextFireTime;
	private final long elapsedTime;
	private final long repeatInterval;
	private final String cronExpression;
	private final boolean paused;
	private final String globalJobId;
	private final List<JobTriggerInformations> triggerInformations;

	JobInformations(JobDetail jobDetail, Map<String, JobExecutionContext> jobExecutionContexts,
			Scheduler scheduler) throws SchedulerException {
		// pas throws SchedulerException ici sinon NoClassDefFoundError
		super();
		assert jobDetail != null;
		assert scheduler != null;
		// rq: jobExecutionContext est non null si le job est en cours d'exécution ou null sinon
		final QuartzAdapter quartzAdapter = QuartzAdapter.getSingleton();
		this.group = quartzAdapter.getJobGroup(jobDetail);
		this.name = quartzAdapter.getJobName(jobDetail);
		this.description = quartzAdapter.getJobDescription(jobDetail);
		this.jobClassName = quartzAdapter.getJobClass(jobDetail).getName();
		this.triggerInformations = new ArrayList<JobTriggerInformations>();

		final List<Trigger> triggers = quartzAdapter.getTriggersOfJob(jobDetail, scheduler);
		boolean groupJobPaused = true;
		Date groupPreviousFireTime = null;
		Date groupNextFireTime = null;
		long groupElapsedTime = -1;
		long groupRepeatInterval = -1;
		String groupCronExpression = null;
		for (final Trigger trigger : triggers) {
			final String jobAndTriggerId = quartzAdapter.getJobAndTriggerId(trigger);
			final JobExecutionContext jobExecutionContext = jobExecutionContexts
					.get(jobAndTriggerId);
			final JobTriggerInformations jobTriggerInformations = new JobTriggerInformations(
					scheduler, trigger, jobExecutionContext);

			this.triggerInformations.add(jobTriggerInformations);

			if (groupPreviousFireTime == null
					|| jobTriggerInformations.getPreviousFireTime() != null && groupPreviousFireTime
							.before(jobTriggerInformations.getPreviousFireTime())) {
				groupPreviousFireTime = jobTriggerInformations.getPreviousFireTime();
			}
			if (groupNextFireTime == null || jobTriggerInformations.getNextFireTime() != null
					&& groupNextFireTime.after(jobTriggerInformations.getNextFireTime())) {
				groupNextFireTime = jobTriggerInformations.getNextFireTime();
			}
			groupJobPaused = groupJobPaused && jobTriggerInformations.isPaused();
			groupElapsedTime = Math.max(groupElapsedTime, jobTriggerInformations.getElapsedTime());
			if (jobTriggerInformations.getRepeatInterval() != -1) {
				groupRepeatInterval = jobTriggerInformations.getRepeatInterval();
			}
			if (jobTriggerInformations.getCronExpression() != null) {
				groupCronExpression = jobTriggerInformations.getCronExpression();
			}
		}

		this.previousFireTime = groupPreviousFireTime;
		this.nextFireTime = groupNextFireTime;
		this.paused = groupJobPaused;
		this.elapsedTime = groupElapsedTime;
		this.repeatInterval = groupRepeatInterval;
		this.cronExpression = groupCronExpression;

		this.globalJobId = PID.getPID() + '_' + Parameters.getHostAddress() + '_'
				+ quartzAdapter.getJobFullName(jobDetail).hashCode();

	}

	private static boolean isQuartzAvailable() {
		try {
			Class.forName("org.quartz.Job");
			Class.forName("org.quartz.impl.SchedulerRepository");
			return true;
		} catch (final ClassNotFoundException e) {
			return false;
		}
	}

	@SuppressWarnings("unchecked")
	static List<JobInformations> buildJobInformationsList() {
		if (!QUARTZ_AVAILABLE) {
			return Collections.emptyList();
		}
		final List<JobInformations> result = new ArrayList<JobInformations>();
		try {
			final QuartzAdapter quartzAdapter = QuartzAdapter.getSingleton();
			for (final Scheduler scheduler : getAllSchedulers()) {
				final Map<String, JobExecutionContext> currentlyExecutingJobsByContextId = new LinkedHashMap<String, JobExecutionContext>();
				for (final JobExecutionContext currentlyExecutingJob : (List<JobExecutionContext>) scheduler
						.getCurrentlyExecutingJobs()) {
					final String jobAndTriggerId = quartzAdapter
							.getJobAndTriggerId(currentlyExecutingJob);
					currentlyExecutingJobsByContextId.put(jobAndTriggerId, currentlyExecutingJob);
				}
				try {
					for (final JobDetail jobDetail : quartzAdapter
							.getAllJobsOfScheduler(scheduler)) {
						result.add(new JobInformations(jobDetail, currentlyExecutingJobsByContextId,
								scheduler));
					}
				} catch (final Exception e) {
					// si les jobs sont persistés en base de données, il peut y avoir une exception
					// dans scheduler.getJobGroupNames(), par exemple si la base est arrêtée
					LOG.warn(e.toString(), e);
					return Collections.emptyList();
				}
			}
		} catch (final Exception e) {
			throw new IllegalStateException(e);
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	public static List<Scheduler> getAllSchedulers() {
		return new ArrayList<Scheduler>(SchedulerRepository.getInstance().lookupAll());
	}

	public String getGlobalJobId() {
		return globalJobId;
	}

	public String getName() {
		return name;
	}

	public String getGroup() {
		return group;
	}

	public String getDescription() {
		return description;
	}

	public String getJobClassName() {
		return jobClassName;
	}

	public long getElapsedTime() {
		return elapsedTime;
	}

	boolean isCurrentlyExecuting() {
		return elapsedTime >= 0;
	}

	public Date getNextFireTime() {
		return nextFireTime;
	}

	public Date getPreviousFireTime() {
		return previousFireTime;
	}

	public long getRepeatInterval() {
		return repeatInterval;
	}

	public String getCronExpression() {
		return cronExpression;
	}

	public boolean isPaused() {
		return paused;
	}

	public List<JobTriggerInformations> getTriggerInformations() {
		return triggerInformations;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return getClass().getSimpleName() + "[name=" + getName() + ", group=" + getGroup() + ']';
	}
}
