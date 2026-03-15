package com.joshlong.mogul.api.jobs;

import com.joshlong.mogul.api.utils.JsonUtils;

import java.util.Map;
import java.util.Objects;

//record JobExecution(
//         Long id,
//        Long mogulId, String jobName, Map<String, Object> context) {
//
//
//    public <T> T getParameter (String paramName, Class<T> type) {
//        var value = context.get(paramName);
//        if (null == value) {
//            return null;
//        }
//        return (T) JsonUtils.read(value,type );
//
//    }
//}
public class JobExecution {

	private final Long id;

	private final Long mogulId;

	private final String jobName;

	private final Map<String, String> context;

	JobExecution(Long id, Long mogulId, String jobName, Map<String, String> context) {
		this.id = id;
		this.mogulId = mogulId;
		this.jobName = jobName;
		this.context = context;
	}

	public Long id() {
		return id;
	}

	public Long mogulId() {
		return mogulId;
	}

	public String jobName() {
		return jobName;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass())
			return false;
		JobExecution that = (JobExecution) o;
		return Objects.equals(id, that.id) && Objects.equals(mogulId, that.mogulId)
				&& Objects.equals(jobName, that.jobName) && Objects.equals(context, that.context);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, mogulId, jobName, context);
	}

	public <T> T getContextAttribute(String paramName, Class<T> type) {
		var param = this.context.get(paramName);
		if (param == null) {
			return null;
		}

		return (T) JsonUtils.read(param, type);
	}

}