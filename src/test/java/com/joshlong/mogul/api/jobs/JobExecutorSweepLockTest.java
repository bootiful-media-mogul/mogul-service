package com.joshlong.mogul.api.jobs;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * the sweep runs on a schedule on every replica, and every replica reads the same
 * {@code event_publication} rows -- so before this lock each node resubmitted the same
 * incomplete {@code JobStartedEvent} and the job ran once per node.
 * <p>
 * these exercise the claim rather than the sweep itself: the real
 * {@code resubmitIncompletePublications} would re-run whatever incomplete events happen
 * to be sitting in the shared development database, and it cannot be mocked away --
 * spring modulith serves {@code IncompleteEventPublications} from the same bean as the
 * {@code applicationEventMulticaster}, so replacing it takes the context down with it.
 */
@SpringBootTest
class JobExecutorSweepLockTest {

	@Autowired
	private JobExecutor jobExecutor;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Test
	void theLockIsTakenWhenNobodyElseHoldsIt() {
		this.transactionTemplate
			.executeWithoutResult(_ -> assertThat(this.jobExecutor.claimSweep()).as("nothing holds it").isTrue());
	}

	/** the point of the exercise: a second node finds it taken and stands down. */
	@Test
	void anotherNodeHoldingTheLockMakesThisOneStandDown() throws Exception {
		// a separate connection in its own transaction is what "another node" amounts
		// to as far as a postgres advisory lock is concerned.
		try (var otherNode = this.dataSource.getConnection()) {
			otherNode.setAutoCommit(false);
			assertThat(tryTakeSweepLock(otherNode)).as("the other node claims it first").isTrue();
			this.transactionTemplate.executeWithoutResult(
					_ -> assertThat(this.jobExecutor.claimSweep()).as("this node must stand down").isFalse());
			otherNode.rollback();
		}
	}

	/**
	 * guards the {@code _xact_}. a session-level lock would survive the rollback and
	 * wedge every later sweep in the cluster; this one goes back with its transaction.
	 */
	@Test
	void theLockIsReleasedWithTheTransactionThatTookIt() throws Exception {
		try (var otherNode = this.dataSource.getConnection()) {
			otherNode.setAutoCommit(false);
			assertThat(tryTakeSweepLock(otherNode)).isTrue();
			otherNode.rollback();
		}
		this.transactionTemplate.executeWithoutResult(
				_ -> assertThat(this.jobExecutor.claimSweep()).as("the rollback handed it back").isTrue());
	}

	/**
	 * the class-level {@code @Transactional} has to reach this package-private method
	 * through the cglib proxy. if it ever stopped doing so the xact lock would be
	 * released the instant it was taken, the claim would always succeed, and every node
	 * would sweep again -- the original bug, wearing a lock. calling with no transaction
	 * of our own proves the proxy supplies one, because the assertion inside
	 * {@code claimSweep} would throw otherwise.
	 */
	@Test
	void theClaimIsGivenATransactionByTheProxy() {
		assertThat(TransactionSynchronizationManager.isActualTransactionActive()).as("none out here").isFalse();
		assertThat(this.jobExecutor.claimSweep()).as("the proxy must supply the transaction").isTrue();
	}

	private static boolean tryTakeSweepLock(Connection connection) throws Exception {
		try (var statement = connection.prepareStatement("select pg_try_advisory_xact_lock(?)")) {
			statement.setLong(1, JobExecutor.INCOMPLETE_EVENTS_SWEEP_LOCK);
			try (var results = statement.executeQuery()) {
				assertThat(results.next()).isTrue();
				return results.getBoolean(1);
			}
		}
	}

}
