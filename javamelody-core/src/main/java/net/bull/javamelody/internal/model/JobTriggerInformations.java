package net.bull.javamelody.internal.model;

import java.util.Date;

import org.quartz.CronTrigger;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

public class JobTriggerInformations {
	private final String group;
	private final String name;
	private final String description;
	private final long elapsedTime;
	private final Date previousFireTime;
	private final Date nextFireTime;
	private final long repeatInterval;
	private final String cronExpression;
	private final boolean paused;

	JobTriggerInformations(Scheduler scheduler, Trigger trigger,
			JobExecutionContext jobExecutionContext) throws SchedulerException {
		final QuartzAdapter quartzAdapter = QuartzAdapter.getSingleton();
		this.group = quartzAdapter.getTriggerGroup(trigger);
		this.name = quartzAdapter.getTriggerName(trigger);
		this.description = quartzAdapter.getTriggerDescription(trigger);
		this.previousFireTime = quartzAdapter.getTriggerPreviousFireTime(trigger);
		this.nextFireTime = quartzAdapter.getTriggerNextFireTime(trigger);

		String cronTriggerExpression = null;
		long simpleTriggerRepeatInterval = -1;
		if (trigger instanceof CronTrigger) {
			cronTriggerExpression = quartzAdapter.getCronTriggerExpression((CronTrigger) trigger);
		} else if (trigger instanceof SimpleTrigger) {
			simpleTriggerRepeatInterval = quartzAdapter
					.getSimpleTriggerRepeatInterval((SimpleTrigger) trigger);
		}
		this.repeatInterval = simpleTriggerRepeatInterval;
		this.cronExpression = cronTriggerExpression;
		this.paused = quartzAdapter.isTriggerPaused(trigger, scheduler);
		if (jobExecutionContext == null) {
			this.elapsedTime = -1;
		} else {
			this.elapsedTime = System.currentTimeMillis()
					- quartzAdapter.getContextFireTime(jobExecutionContext).getTime();
		}
	}

	public Date getPreviousFireTime() {
		return previousFireTime;
	}

	public Date getNextFireTime() {
		return nextFireTime;
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

	public String getGroup() {
		return group;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public long getElapsedTime() {
		return elapsedTime;
	}

	@Override
	public String toString() {
		return "JobTriggerInformations [previousFireTime=" + previousFireTime + ", nextFireTime="
				+ nextFireTime + ", repeatInterval=" + repeatInterval + ", cronExpression="
				+ cronExpression + ", paused=" + paused + "]";
	}

}
