package com.joshlong.mogul.api.mogul;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Transactional
class DefaultMogulStatusService implements MogulStatusService {

    private final JdbcClient db;

    private final MogulStatusRowMapper mogulStatusRowMapper = new MogulStatusRowMapper();

    DefaultMogulStatusService(JdbcClient db) {
        this.db = db;
        Assert.notNull(this.db, "the db is null");
    }

    @Override
    public MogulStatus today(Long mogulId) {
        var today = LocalDate.now();
        this.db//
                .sql("insert into mogul_status(mogul_id, date) values (?,?) on conflict (mogul_id, date) do nothing")//
                .params(mogulId, today)//
                .update();
        var status = this.getMogulStatusByDate(mogulId, today);
        Assert.notNull(status, "the status for mogul [" + mogulId + "] on [" + today + "] should exist by now");
        return status;
    }

    @Override
    public MogulStatus getMogulStatusById(Long id) {
        var all = this.db //
                .sql("select * from mogul_status where id = ?") //
                .param(id) //
                .query(this.mogulStatusRowMapper) //
                .list();
        return all.isEmpty() ? null : all.getFirst();
    }

    @Override
    public MogulStatus getMogulStatusByDate(Long mogulId, LocalDate date) {
        var all = this.db //
                .sql("select * from mogul_status where mogul_id = ? and date = ?") //
                .params(mogulId, date) //
                .query(this.mogulStatusRowMapper) //
                .list();
        return all.isEmpty() ? null : all.getFirst();
    }

    @Override
    public Collection<MogulStatus> getRecentMogulStatuses(Long mogulId, int limit) {
        if (limit <= 0)
            return List.of();
        return this.db //
                .sql("select * from mogul_status where mogul_id = ? order by date desc limit ?") //
                .params(mogulId, limit) //
                .query(this.mogulStatusRowMapper) //
                .list();
    }

    private static class MogulStatusRowMapper implements RowMapper<MogulStatus> {

        @Override
        public MogulStatus mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new MogulStatus(rs.getLong("id"), rs.getLong("mogul_id"), rs.getObject("date", LocalDate.class),
                    rs.getTimestamp("created"));
        }

    }

}
