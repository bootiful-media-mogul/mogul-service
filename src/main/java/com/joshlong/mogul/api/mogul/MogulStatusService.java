package com.joshlong.mogul.api.mogul;

import java.time.LocalDate;
import java.util.Collection;

public interface MogulStatusService {

	MogulStatus today(Long mogulId);

	MogulStatus getMogulStatusById(Long id);

	MogulStatus getMogulStatusByDate(Long mogulId, LocalDate date);

	Collection<MogulStatus> getRecentMogulStatuses(Long mogulId, int limit);

}
