package org.stagemonitor.alerting.incident;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.stagemonitor.alerting.check.Check;
import org.stagemonitor.alerting.check.CheckResult;
import org.stagemonitor.core.MeasurementSession;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE;

@JsonAutoDetect(fieldVisibility = ANY, getterVisibility = ANY, setterVisibility = NONE)
public class Incident {

	private int version = 1;
	private Date firstFailureAt;
	private Date resolvedAt;
	private CheckResult.Status oldStatus;
	private CheckResult.Status newStatus;
	private String checkId;
	private String checkName;
	private int consecutiveFailures;
	@JsonIgnore
	private Map<String, CheckResults> checkResultsByMeasurementSessionId = new HashMap<String, CheckResults>();


	public static CheckResult.Status getMostSevereStatus(Collection<Incident> incidents) {
		CheckResult.Status mostSevereStatus = CheckResult.Status.OK;
		for (Incident incident : incidents) {
			if (incident.getNewStatus().isMoreSevere(mostSevereStatus)) {
				mostSevereStatus = incident.getNewStatus();
			}
		}
		return mostSevereStatus;
	}

	public Incident() {
	}

	@JsonCreator
	public Incident(@JsonProperty("checkResults") Collection<CheckResults> checkResults) {
		for (CheckResults checkResult : checkResults) {
			checkResultsByMeasurementSessionId.put(checkResult.getMeasurementSession().getId(), checkResult);
		}
	}

	public Incident(Check check, MeasurementSession measurementSession, List<CheckResult> checkResults) {
		this.newStatus = CheckResult.getMostSevereStatus(checkResults);
		this.checkId = check.getId();
		this.checkName = check.getName();
		this.firstFailureAt = new Date();

		setCheckResults(measurementSession, checkResults, 0);
	}

	public Incident(Incident previousIncident, MeasurementSession measurementSession, List<CheckResult> checkResults) {
		version = previousIncident.version + 1;
		oldStatus = previousIncident.newStatus;
		checkId = previousIncident.checkId;
		checkName = previousIncident.checkName;
		checkResultsByMeasurementSessionId = previousIncident.checkResultsByMeasurementSessionId;
		firstFailureAt = previousIncident.getFirstFailureAt();
		setCheckResults(measurementSession, checkResults, previousIncident.consecutiveFailures);
	}

	private void setCheckResults(MeasurementSession measurementSession, List<CheckResult> checkResults, int previousConsecutiveFailures) {
		if (checkResults.isEmpty()) {
			checkResultsByMeasurementSessionId.remove(measurementSession.getId());
		} else if (checkResultsByMeasurementSessionId.containsKey(measurementSession.getId())) {
			checkResultsByMeasurementSessionId.put(measurementSession.getId(),
					new CheckResults(checkResultsByMeasurementSessionId.get(measurementSession.getId()), checkResults));
		} else {
			checkResultsByMeasurementSessionId.put(measurementSession.getId(), new CheckResults(measurementSession, checkResults));
		}
		newStatus = getMostSevereStatus();
		if (newStatus == CheckResult.Status.OK) {
			resolvedAt = new Date();
		}
		consecutiveFailures = Math.max(previousConsecutiveFailures, getConsecutiveFailuresFromCheckResults());
	}

	public Collection<CheckResults> getCheckResults() {
		return checkResultsByMeasurementSessionId.values();
	}

	public Collection<String> getHosts() {
		Set<String> hosts = new TreeSet<String>();
		for (CheckResults checkResult : checkResultsByMeasurementSessionId.values()) {
			hosts.add(checkResult.getMeasurementSession().getHostName());
		}
		return hosts;
	}

	public Collection<String> getInstances() {
		Set<String> instances = new TreeSet<String>();
		for (CheckResults checkResult : checkResultsByMeasurementSessionId.values()) {
			instances.add(checkResult.getMeasurementSession().getInstanceName());
		}
		return instances;
	}

	private CheckResult.Status getMostSevereStatus() {
		LinkedList<CheckResult> results = new LinkedList<CheckResult>();
		for (CheckResults checkResults : checkResultsByMeasurementSessionId.values()) {
			results.addAll(checkResults.getResults());
		}
		return CheckResult.getMostSevereStatus(results);
	}

	@JsonIgnore
	private int getConsecutiveFailuresFromCheckResults() {
		int consecutiveFailures = 0;
		for (CheckResults checkResultByInstance : checkResultsByMeasurementSessionId.values()) {
			consecutiveFailures = Math.max(consecutiveFailures, checkResultByInstance.getConsecutiveFailures());
		}
		return consecutiveFailures;
	}

	public int getConsecutiveFailures() {
		return consecutiveFailures;
	}

	/**
	 * The version is used for optimistic concurrency control
	 *
	 * @return the version
	 */
	public int getVersion() {
		return version;
	}

	public void setVersion(int version) {
		this.version = version;
	}

	public Date getFirstFailureAt() {
		return firstFailureAt;
	}

	public void setFirstFailureAt(Date firstFailureAt) {
		this.firstFailureAt = firstFailureAt;
	}

	public CheckResult.Status getOldStatus() {
		return oldStatus;
	}

	public void setOldStatus(CheckResult.Status oldStatus) {
		this.oldStatus = oldStatus;
	}

	public CheckResult.Status getNewStatus() {
		return newStatus;
	}

	public void setNewStatus(CheckResult.Status newStatus) {
		this.newStatus = newStatus;
	}

	/**
	 * The id of the corresponding {@link org.stagemonitor.alerting.check.Check}.
	 * It is also used as the primary key for the incident.
	 *
	 * @return the id of the corresponding {@link org.stagemonitor.alerting.check.Check}.
	 */
	public String getCheckId() {
		return checkId;
	}

	public void setCheckId(String checkId) {
		this.checkId = checkId;
	}

	public String getCheckName() {
		return checkName;
	}

	public void setCheckName(String checkName) {
		this.checkName = checkName;
	}

	public boolean hasStageChange() {
		return oldStatus != newStatus;
	}

	public Date getResolvedAt() {
		return resolvedAt;
	}

	public void setResolvedAt(Date resolvedAt) {
		this.resolvedAt = resolvedAt;
	}

	public int getFailedChecks() {
		int failedChecks = 0;
		for (CheckResults checkResults : getCheckResults()) {
			failedChecks += checkResults.getResults().size();
		}
		return failedChecks;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Incident incident = (Incident) o;

		if (version != incident.version) return false;
		return !(checkId != null ? !checkId.equals(incident.checkId) : incident.checkId != null);

	}

	@Override
	public int hashCode() {
		int result = version;
		result = 31 * result + (checkId != null ? checkId.hashCode() : 0);
		return result;
	}

	@JsonIgnore
	public Incident getIncidentWithPreviousVersion() {
		final Incident incident = new Incident();
		incident.setCheckId(checkId);
		incident.setVersion(version - 1);
		return incident;
	}

	public boolean isAlertIncident(Check check) {
		if (isBackToOk() && hasEnoughConsecutiveFailures(check)) {
			return true;
		}
		if (hasStageChange() && hasEnoughConsecutiveFailures(check)) {
			return true;
		}
		return getConsecutiveFailures() == check.getAlertAfterXFailures();
	}

	private boolean hasEnoughConsecutiveFailures(Check check) {
		return getConsecutiveFailures() >= check.getAlertAfterXFailures();
	}

	private boolean isBackToOk() {
		return hasStageChange() && newStatus == CheckResult.Status.OK;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder()
				.append("Incident for check group '").append(checkName).append("':\n")
				.append("firstFailureAt=").append(firstFailureAt).append("\n");
		if (resolvedAt == null) {
			sb.append("resolvedAt=").append(resolvedAt).append("\n");
		}
		sb.append("oldStatus=").append(oldStatus).append("\n")
				.append("newStatus=").append(newStatus).append("\n");
		if (!checkResultsByMeasurementSessionId.isEmpty()) {
			sb.append("host|instance|status|description|current value\n")
					.append("----|--------|------|-----------|-------------\n");
			for (CheckResults checkResult : checkResultsByMeasurementSessionId.values()) {
				MeasurementSession measurement = checkResult.getMeasurementSession();
				for (CheckResult result : checkResult.getResults()) {
					sb.append(measurement.getHostName()).append('|')
							.append(measurement.getInstanceName()).append('|')
							.append(result.getStatus()).append('|')
							.append(result.getFailingExpression()).append('|')
							.append(result.getCurrentValue()).append("\n");
				}
			}
		}
		return sb.toString();
	}
}
